package com.ibm.watson.aiops.connectors.github;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.cert.CertificateException;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONException;
import org.json.JSONObject;

import io.cloudevents.CloudEvent;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpsParameters;


public class WebhookServer implements Runnable {

    static final Logger logger = Logger.getLogger(WebhookServer.class.getName());
    private HttpServer httpServer = null;
    private HttpsServer httpsServer = null;
    private Connector connector = null;
    private String connectorID = null;
    private char[] password;

    public WebhookServer(Connector connector, String connectorID) {
        this.connector = connector;
        this.connectorID = connectorID;
        password = connector.genSecret().toCharArray();
    }

    @Override
    public void run() {

        logger.log(Level.INFO, "Server starting...");
        
        if (System.getenv("USE_HTTP") != null) {
            createHttpServer();
        } else {
            createHttpsServer();
        }  
    }

    public void stop() {
        if (httpServer != null) {
            logger.log(Level.INFO, "Http server stopping...");
            httpServer.stop(0);
            logger.log(Level.INFO, "Http server at 8080 stopped.");
        } else if (httpsServer != null) {
            logger.log(Level.INFO, "Https server stopping...");
            httpsServer.stop(0);
            logger.log(Level.INFO, "Https server at 8080 stopped.");
        } 
    }

    private void createHttpServer() {
        try {
            httpServer = HttpServer.create(new InetSocketAddress(8080), 0);

            logger.log(Level.INFO, "Http server started at 8080.");

            httpServer.createContext("/webhook/" + connectorID, new WebhookHandler());
            httpServer.createContext("/healthz" , new HealthzHandler());
            httpServer.setExecutor(null);
            httpServer.start();
        } catch (IOException e) { 
            logger.log(Level.SEVERE, "Http server failed to start at 8080.");
            e.printStackTrace();
        }
    }

    private void createHttpsServer() {
        try {
            java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
            
            httpsServer = HttpsServer.create(new InetSocketAddress(8080), 0);

            SSLContext sslContext = SSLContext.getInstance("TLS");

            KeyStore ks = createKeyStore(new FileInputStream(getCertPath()), new FileInputStream(getPrivateKeyPath()));

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, password);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);

            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            
            httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                public void configure(HttpsParameters params) {
                    try {
                        SSLContext context = getSSLContext();
                        SSLEngine engine = context.createSSLEngine();
                        params.setNeedClientAuth(false);
                        params.setCipherSuites(engine.getEnabledCipherSuites());
                        params.setProtocols(engine.getEnabledProtocols());

                        SSLParameters sslParameters = context.getSupportedSSLParameters();
                        params.setSSLParameters(sslParameters);

                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Failed to create HTTPS port.");
                        e.printStackTrace();
                    }
                }
            });
            
            logger.log(Level.INFO, "https server started at 8080.");

            httpsServer.createContext("/webhook/" + connectorID, new WebhookHandler());
            httpsServer.createContext("/healthz" , new HealthzHandler());
            httpsServer.setExecutor(null);
            httpsServer.start();
        } catch (IOException | KeyManagementException | KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException | CertificateException  e) {
            logger.log(Level.SEVERE, "Server failed to start at 8080.");
            e.printStackTrace();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to create keyStore and load cert and private key");
            logger.log(Level.SEVERE, "Server failed to start at 8080.");
            e.printStackTrace();
        }
    }

    private KeyStore createEmptyKeyStore() throws IOException, GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null,null);
        return keyStore;
    }

    private X509Certificate readCert(InputStream certInput) throws IOException, GeneralSecurityException {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate)factory.generateCertificate(certInput);
        return cert;
    }

    private PrivateKey readPrivateKey(InputStream keyIn) throws IOException, GeneralSecurityException {
    
        byte[] bytesFile = IOUtils.toByteArray(keyIn);
        String stringFile = new String(bytesFile);
    
        //Remove between BEGIN/END
        Pattern regexParser = Pattern.compile("(?m)(?s)^---*BEGIN.*---*$(.*)^---*END.*---*$.*");
        String encodedKey = regexParser.matcher(stringFile).replaceFirst("$1");
    
        byte[] decodedKey = Base64.getMimeDecoder().decode(encodedKey);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decodedKey);
    
        //Use the KeyFactor to generate
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
    
        return privateKey;
    }

    private KeyStore createKeyStore(InputStream certInput, InputStream keyIn) throws IOException, GeneralSecurityException {
        KeyStore keyStore = createEmptyKeyStore();
        X509Certificate publicCert = readCert(certInput);
        PrivateKey privateKey = readPrivateKey(keyIn);
        
        keyStore.setCertificateEntry("publicCert", publicCert);
        keyStore.setKeyEntry("privateKey", privateKey, password, new Certificate[]{publicCert});
    
        return keyStore;
    }

    private String getCertPath() {
        String path = System.getenv("WEBHOOK_CERT_PATH");
        if (path != null) {
            return path;
        }

        // Default
        return  "/webhook/tls.crt";
    }

    private String getPrivateKeyPath() {
        String path = System.getenv("WEBHOOK_KEY_PATH");
        if (path != null) {
            return path;
        }

        // Default
        return "/webhook/tls.key";
    }

    private class HealthzHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        }
    }
    
    private class WebhookHandler implements HttpHandler {
        private final String GITHUB_EVENT_HEADER = "X-GitHub-Event";
        private final String GITHUB_SIGNATURE_HEADER = "X-Hub-Signature";
        @Override
        public void handle(HttpExchange exchange) throws IOException {

            try{
                
                // Only requests only if live data is on
                if (connector.isLiveDataOn()) {
                    // Get headers
                    Headers headers = exchange.getRequestHeaders();
                    String signature = headers.get(GITHUB_SIGNATURE_HEADER).get(0);
                    String event = headers.get(GITHUB_EVENT_HEADER).get(0);
                    
                    // Get req body 
                    StringBuilder sb = new StringBuilder();
                    InputStream ios = exchange.getRequestBody();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(ios));
                    while (reader.ready()) {
                        sb.append(reader.readLine());
                    }
                
                    String body = sb.toString();

                    // Convert body into json object
                    JSONObject payload = new JSONObject(body);

                    try {
                        validateSignature(signature, body.getBytes());
                    }catch (GeneralSecurityException e) {
                        logger.log(Level.SEVERE, e.getMessage());
                        exchange.sendResponseHeaders(401, 0);
                        exchange.getResponseBody().close();
                        return;
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, e.getMessage());
                        exchange.sendResponseHeaders(500, 0);
                        exchange.getResponseBody().close();
                        return;
                    }

                    handleWebhookRequest(event, payload);  
                    
                }

                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().close();

            }
            catch (Exception e) {
                logger.log(Level.SEVERE, e.getMessage());
                exchange.sendResponseHeaders(400, 0);
                exchange.getResponseBody().close();
            }
        }

        private void validateSignature(String signature, byte[] payload) throws GeneralSecurityException {
            final String HMAC_SHA = "SHA1";
            if (signature != null) {

                // hex decode signature (minus sha*= prefix) to byte[]
                signature = signature.substring(HMAC_SHA.length() + 1);
                byte[] actualSig = Hex.decode(signature);

                // compute the expected signature
                Mac hmac = Mac.getInstance("Hmac" + HMAC_SHA);
                hmac.init(new SecretKeySpec(WebhookAction.getEncodedSecret(connector.getWebhookSecret()).getBytes(), "Hmac" + HMAC_SHA));
                byte[] expectedSig = hmac.doFinal(payload);

                if (MessageDigest.isEqual(actualSig, expectedSig)) {
                    logger.log(Level.INFO, "-> Signature is valid");
                    return;
                }
            }
            throw new SignatureException("Signature validation failed");
        }

        private void handleWebhookRequest(String event, JSONObject payload) {
            // If live is off, do not process the request
            if (!connector.getConnectorConfiguration().live){
                return;
            }

            // handle supported events
            switch (event) {
                case "repository": 
                    handleRepositoryWebhook(payload);
                    break;
                // Treat the bollow events as noop
                /*case "issues": 
                    handleIssueWebhook(payload);
                    break;
                case "pull_request": 
                    handlePullRequestWebhook(payload);
                    break;
                case "push": 
                    handleCommitWebhook(payload);
                    break;
                case "pull_request_review_comment": 
                case "pull_request_review":
                case "issue_comment":
                    handleCommentWebhook(payload);
                    break;*/
            }
        }

        private void handleRepositoryWebhook(JSONObject payload) {
            String action = getString(payload, "action");
            if (action != null) {
                CloudEvent ce;
                switch (action) {
                    case "created":
                    case "renamed":
                    case "deleted":
                        // for created, renamed, or deleted, simply emit the repository
                        String type = action.equals("deleted") ? ConnectorConstants.REPO_DEL : ConnectorConstants.REPO_DISC;
                        ce = getRepositoryCE(payload, type);
                        if (ce != null) {
                            logger.log(Level.INFO, "Emitting cloud event");
                            connector.emitCloudEvent(ConnectorConstants.OUTPUT_REPO_TOPIC, null, ce);
                        }
                        break;
                    case "transferred":
                        // TODO: // for transferred, go through entire flow 
                        break;  
                }
            }
        }

        private CloudEvent getRepositoryCE(JSONObject payload, String type) {
            JSONObject repository = getJSONObject(payload, "repository");
            if (repository != null) {
                String desc = getString(repository, "description");
                desc = desc != null ? desc : "";
                Boolean boolPriv = getBoolean(repository, "private");
                String priv = boolPriv != null ? Boolean.toString(boolPriv) : "";
                Boolean boolFork = getBoolean(repository, "fork");
                String fork = boolFork != null ? Boolean.toString(boolFork) : "";
                String defaultBranch = getString(repository, "default_branch");
                defaultBranch = defaultBranch != null ? defaultBranch : "";
                String createdAt = getString(repository, "created_at");
                createdAt = createdAt != null ? createdAt : "";
                String updatedAt = getString(repository, "updated_at");
                updatedAt = updatedAt != null ? updatedAt : "";
                String pushedAt = getString(repository, "updated_at");
                pushedAt = pushedAt != null ? pushedAt : "";
                String language = getString(repository, "language");
                language = language != null ? language : "";

                JSONObject json = new JSONObject()
                    .put(ConnectorConfiguration.PROPERTY_TYPE, type)
                    .put("description", desc)
                    .put("private", priv)
                    .put("fork", fork)
                    .put("default_branch", defaultBranch)
                    .put("created_at", createdAt)
                    .put("updated_at", updatedAt)
                    .put("pushed_at", pushedAt)
                    .put("language", language);
                setParentAndRepoAndId(payload, json);
                return connector.createEvent(0, type, json.toString());
            } 
            return null;		
        }

        private void handleIssueWebhook(JSONObject payload) {
            String action = getString(payload, "action");
            if (action != null) {
                if (action.equals("opened")) {
                    JSONObject issue = getJSONObject(payload, "issue");
                    if (issue != null) {
                        Integer comments = getInt(issue, "comments");
                        if (comments != null) {
                            CloudEvent ce;
                            if (comments == 0) {
                                // new issue with no comments, just emit the issue
                                ce = getIssueCE(payload, issue, ConnectorConstants.ISSUE_DISC);
                                if (ce != null) {
                                    logger.log(Level.INFO, "Emitting cloud event");
                                    connector.emitCloudEvent(ConnectorConstants.DISC_TOPIC, "partition-0", ce);
                                }
                            } else {
                                // TODO: // transferred issue, go through entire flow 
                            }
                        }
                    }
                }
            }
        }

        private CloudEvent getIssueCE(JSONObject payload, JSONObject issue, String type) {
            String title = getString(issue, ConnectorConfiguration.PROPERTY_TITLE);
            title = title != null ? title : "";
            String body = getString(issue, ConnectorConfiguration.PROPERTY_BODY);
            body = body != null ? body : "";

            JSONObject json = new JSONObject()
                // TODO: add repo_id, issue_id, repo, org, uri 
                .put(ConnectorConfiguration.PROPERTY_TYPE, type)
                .put(ConnectorConfiguration.PROPERTY_TITLE, title)
                .put(ConnectorConfiguration.PROPERTY_BODY, body);
            setParentAndRepoAndId(payload, json);
            return connector.createEvent(0, type, json.toString()); 
        }

        private void handlePullRequestWebhook(JSONObject payload) {
            String action = getString(payload, "action");
            if (action != null) {
                CloudEvent ce;
                switch (action) {
                    case "synchronize": 
                        ce = getPullRequestCE(payload, ConnectorConstants.PR_DISC, true);
                        if (ce != null) {
                            logger.log(Level.INFO, "Emitting cloud event");
                            connector.emitCloudEvent(ConnectorConstants.DISC_TOPIC, "partition-0", ce);
                        }
                        // TODO: get references
                        break;
                    case "opened":
                        ce = getPullRequestCE(payload, ConnectorConstants.PR_DISC, false);
                        if (ce != null) {
                            logger.log(Level.INFO, "Emitting cloud event");
                            connector.emitCloudEvent(ConnectorConstants.DISC_TOPIC, "partition-0", ce);
                        }
                        break;
                }
            }
        }

        private CloudEvent getPullRequestCE(JSONObject payload, String type, boolean update) {
            JSONObject pr = getJSONObject(payload, "pull_request");
            if (pr != null) {
                String body = getString(pr , ConnectorConfiguration.PROPERTY_BODY);
                body = body != null ? body : "";
                String title = getString(pr , ConnectorConfiguration.PROPERTY_TITLE);
                title = title != null ? title : "";
                String mergedAt = getString(pr , "merged_at");
                String source = "";
                JSONObject head = getJSONObject(pr , "head");
                if (head != null) {
                    String headRef = getString(head, "ref");
                    if (headRef != null) {
                        source = headRef;
                    }
                }
                String target = "";
                JSONObject base = getJSONObject(pr , "base");
                if (base != null) {
                    String baseRef = getString(base, "ref");
                    if (baseRef != null) {
                        target = baseRef;
                    }
                }
                Integer intId = getInt(pr , "id");
                String itemId = intId != null ? Integer.toString(intId) : "";
                
                JSONObject json = new JSONObject()
                    // TODO: add below fields
                    //.put("uri", )
                    //.put("commits", )
                    .put(ConnectorConfiguration.PROPERTY_TYPE, type)
                    .put("item_id", itemId)
                    .put("source", source)
                    .put("target", target)
                    .put(ConnectorConfiguration.PROPERTY_TITLE, title)
                    .put(ConnectorConfiguration.PROPERTY_BODY, body)
                    .put("update", Boolean.toString(update));
                setParentAndRepoAndId(payload, json);
                return connector.createEvent(0, type, json.toString());
            } 
            return null;	
        }	

        private void handleCommitWebhook(JSONObject payload) {
            List<Object> commits = getList(payload, "commits");
            if (commits != null) {
                CloudEvent ce;
                for (int i=0; i<commits.size(); i++) {
                    @SuppressWarnings("unchecked")
                    Map<String, ?> mapCommit = (Map<String, ?>) commits.get(i);
                    JSONObject commit = new JSONObject(mapCommit);
                    ce = getCommitCE(payload, commit, ConnectorConstants.COMMIT_DISC);
                    if (ce != null) {
                        logger.log(Level.INFO, "Emitting cloud event");
                        connector.emitCloudEvent(ConnectorConstants.DISC_TOPIC, "partition-0", ce);
                    }
                }
            }
        }

        private CloudEvent getCommitCE(JSONObject payload, JSONObject commit, String type) {
            String id = getString(commit, "id");
            id = id != null ? id : "";
            String url = getString(commit, "url");
            url = url != null ? url : "";
            String message = getString(commit, "message");
            message = message != null ? message : "";
            String timestamp = getString(commit, "timestamp");
            timestamp = timestamp != null ? timestamp : "";
            String branch = "";
            String ref = getString(payload, "ref");
            if (ref != null) {
                String[] tokens = ref.split("/");
                if (tokens.length == 3) {
                    branch = tokens[2];
                }
            }

            JSONObject json = new JSONObject()
                .put(ConnectorConfiguration.PROPERTY_TYPE, type)
                .put("branch", branch)
                .put("commit_id", id)
                .put("message", message)
                .put("timestamp", timestamp)
                .put("raw_data", commit.toString());
            setParentAndRepoAndId(payload, json);
            return connector.createEvent(0, type, json.toString()); 
        }

        private void handleCommentWebhook(JSONObject payload) {
            String action = getString(payload, "action");
            if (action != null) {
                JSONObject comment = getJSONObject(payload, "review");
                if (comment == null) {
                    comment = getJSONObject(payload, "comment");
                }

                if (comment != null) {
                    String type = action.equals("deleted") ? ConnectorConstants.COMMENT_DEL : ConnectorConstants.COMMENT_DISC;
                    CloudEvent ce = getCommentCE(payload, comment, type);
                    if (ce != null) {
                        logger.log(Level.INFO, "Emitting cloud event");
                        connector.emitCloudEvent(ConnectorConstants.DISC_TOPIC, "partition-0", ce);
                    }
                }
            }
        }

        private CloudEvent getCommentCE(JSONObject payload, JSONObject comment, String type) {
            String body = getString(comment , ConnectorConfiguration.PROPERTY_BODY);
            body = body != null ? body : "";
            if (body.isBlank()) {
                return null;
            }
            Integer intId = getInt(comment, "id");
            String id = intId != null ? Integer.toString(intId) : "";
            String htmlUrl = getString(comment, "html_url");
            htmlUrl = htmlUrl != null ? htmlUrl : "";
            String itemType = htmlUrl.contains("/pull/") ? "pr" : "issue";

            JSONObject json = new JSONObject()
                .put(ConnectorConfiguration.PROPERTY_TYPE, type)
                .put("item_type", itemType)
                //.put("comment_type") TODO: add this field
                .put("item_id", id)
                .put(ConnectorConfiguration.PROPERTY_BODY, body);
            setParentAndRepoAndId(payload, json);
            return connector.createEvent(0, type, json.toString()); 
        }

        private void setParentAndRepoAndId(JSONObject payload, JSONObject json) {
            JSONObject repository = getJSONObject(payload, "repository");
            if (repository != null) {
                String fullName = getString(repository, "full_name");
                if (fullName != null) {
                    String[] tokens = fullName.split("/");
                    json.put(ConnectorConfiguration.PROPERTY_PARENT, tokens[0]);
                    json.put(ConnectorConfiguration.PROPERTY_REPO_NAME, tokens[1]);
                }
                Integer id = getInt(repository, "id");
                if (id != null) {
                    json.put("repo_id", Integer.toString(id));
                }
            }
        }

        private JSONObject getJSONObject(JSONObject object, String key) {
            Object value = getObject(object, key);
            if (value == null || value == JSONObject.NULL) {
                return null;
            }
            return (JSONObject) value;
        }

        private String getString(JSONObject object, String key) {
            Object value = getObject(object, key);
            if (value == null || value == JSONObject.NULL) {
                return null;
            }
            return (String) value;
        }

        private Boolean getBoolean(JSONObject object, String key) {
            Object value = getObject(object, key);
            if (value == null || value == JSONObject.NULL) {
                return null;
            }
            return (Boolean) value;
        }

        private Integer getInt(JSONObject object, String key) {
            Object value = getObject(object, key);
            if (value == null || value == JSONObject.NULL) {
                return null;
            }
            return (Integer) value;
        }

        private Long getLong(JSONObject object, String key) {
            Object value = getObject(object, key);
            if (value == null || value == JSONObject.NULL) {
                return null;
            }
            return (Long) value;
        }

        private List<Object> getList(JSONObject object, String key) {
            try{
                List<Object> arr = object.getJSONArray(key).toList();
                if (arr == JSONObject.NULL) {
                    return null;
                }
                return arr;
            } catch (JSONException e) {
                logger.log(Level.SEVERE, e.getMessage());
                return null;
            }
        }

        private Object getObject(JSONObject object, String key) {
            try{
                return object.get(key);
            } catch (JSONException e) {
                logger.log(Level.SEVERE, e.getMessage());
                return null;
            }
        }

    }
}

