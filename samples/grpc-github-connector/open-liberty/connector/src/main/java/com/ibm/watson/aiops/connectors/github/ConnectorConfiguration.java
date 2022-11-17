package com.ibm.watson.aiops.connectors.github;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONObject;
import org.kohsuke.github.GHEvent;

import java.io.File;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.util.Base64;
import javax.crypto.Cipher;
import io.fusionauth.pem.domain.PEM;

public class ConnectorConfiguration implements Cloneable {
	
	// Required cloud event attributes
	public static final String PROPERTY_TYPE = "type";
	public static final String PROPERTY_DATA = "data";
	public static final String PROPERTY_CONNECTION_ID = "connectionid"; 
	public static final String PROPERTY_COMPONENT_NAME = "componentname";
	protected String connectionid;
	protected String componentname;
	
	// Connection create cloud event attributes
	public static final String PROPERTY_LIVE = "live";
	public static final String PROPERTY_PATTERNS = "patterns";
	public static final String PROPERTY_TARGET = "target";
	public static final String PROPERTY_URI = "uri";
	public static final String PROPERTY_TOKEN = "token";
	public static final String PROPERTY_VAULT_REF = "vault_ref";
	public static final String PROPERTY_VAULT_REFERENCE = "vault_reference";
	public static final String PROPERTY_VAULT_REFERENCE_PATH = "path";
	public static final String PROPERTY_VAULT_REFERENCE_KEY = "key";
	public static final String PROPERTY_INSECURE_WEBHOOK = "insecure_webhook";
	protected boolean live;
	protected String target;
	protected String uri;
	protected String token;
	protected String vaultRef;
	protected JSONObject vaultReference;
	protected String vaultReferencePath;
	protected String vaultReferenceKey;
	protected boolean insecureWebhook = false;
	protected List<String> patterns;

	public static final int DEFAULT_ID = -1;
	
	// Issue/Comment create action cloud event attributes
	public static final String PROPERTY_PARENT = "parent";
	public static final String PROPERTY_REPO_NAME = "repo_name";
	public static final String PROPERTY_TITLE = "title";
	public static final String PROPERTY_BODY = "body";
	public static final String PROPERTY_LABELS = "labels";
	public static final String PROPERTY_ITEM_ID = "item_id";
	public static final String PROPERTY_ISSUE_TYPE = "issuetype";
	protected String parent;
    protected String repoName;
	protected String title;
	protected String body;
	protected List<String> labels;
	protected int itemId = DEFAULT_ID;
	protected String issueType;

	// Webhook action cloud event attributes
	public static final String PROPERTY_WEBHOOK_ACTION = "webhook_action";
	public static final String PROPERTY_EVENTS = "events";
	public static final String PROPERTY_WEBHOOK_ID = "webhook_id";
	public static final String PROPERTY_WEBHOOK_URL = "webhook_url";
	protected String webhookAction;
	protected List<GHEvent> events;
	protected int webhookId = DEFAULT_ID;
	protected String webhookURL;

	protected String tokenDecodeCertLocation = ConnectorConstants.DEFAULT_TOKEN_DECODE_CERT_PATH;
	

	protected long startTime = System.nanoTime();
	
	static final Logger logger = Logger.getLogger(ConnectorConfiguration.class.getName());

	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}
	
	public long getStartTime() {
		return startTime;
	}
	
	public void loadDataFromJson(String configurationJson) {

		JSONObject json = new JSONObject(configurationJson);
		try {
			// These cloud event attributes are guaranteed to exist
			connectionid = json.getString(PROPERTY_CONNECTION_ID);
			componentname = json.getString(PROPERTY_COMPONENT_NAME);
			
			JSONObject dataJson = json.getJSONObject(PROPERTY_DATA);
			// These cloud event attributes may not exist

			// Extract connection create cloud event attributes
			if (dataJson.has(PROPERTY_LIVE))
				live = dataJson.getBoolean(PROPERTY_LIVE);
			if (dataJson.has(PROPERTY_TARGET))
				target = dataJson.getString(PROPERTY_TARGET);
			if (dataJson.has(PROPERTY_URI))
				uri = dataJson.getString(PROPERTY_URI);
				if (dataJson.has(PROPERTY_TOKEN)){
				
					// For dev testing, it is often easier to use a plaintext token. The end user
					// would not be using an unecrypted token.
					token = dataJson.getString(PROPERTY_TOKEN);
	
					// When deployed via the bundle, the key is found in:
					// /bindings/grpc-bridge/tls.key
					//
					// When deployed locally, the environment variable is
					// grpc-bridge.client-private-key-file
					if (token.startsWith(ConnectorConstants.ENCRYPTED_PREFIX)){
						logger.log(Level.INFO, "Token is encrypted");
						// Remove the encrypt part
						token = token.substring(ConnectorConstants.ENCRYPTED_PREFIX.length());
	
						String tlsKeyPath = System.getProperty(ConnectorConstants.ENV_JWT_CERT_PATH);
						String fileContent = "";
						File jwtCertFile = null;
						 
						if (tlsKeyPath != null && tlsKeyPath.length() > 0){
							logger.log(Level.INFO, "Found environment variable with path to TLS " + tlsKeyPath);
							jwtCertFile = new File(tlsKeyPath);
						}
						else{
							logger.log(Level.INFO, "Using default location " + getTokenDecodeCertLocation());
							// Try to read from default cert location
							// /bindings/grpc-bridge/tls.key
							jwtCertFile = new File(getTokenDecodeCertLocation());
						}
						try {
							fileContent = Files.readString(jwtCertFile.toPath());
	
							PrivateKey privateKey = PEM.decode(fileContent).privateKey;
							Cipher cipher = Cipher.getInstance("RSA");
							cipher.init(Cipher.DECRYPT_MODE, privateKey);
	
							token = new String(cipher.doFinal(Base64.getDecoder().decode(token)));
							logger.log(Level.INFO, "Successfully decrpyted token");
						}
						catch (Exception e){
							// On error et it to null
							token = null;
							logger.log(Level.INFO, "Failed to decrypt token " + e.getMessage());
						}
					}
				}	
			if (dataJson.has(PROPERTY_INSECURE_WEBHOOK))
				insecureWebhook = dataJson.getBoolean(PROPERTY_INSECURE_WEBHOOK);
			if (dataJson.has(PROPERTY_VAULT_REF))
				vaultRef = dataJson.getString(PROPERTY_VAULT_REF);
			if (dataJson.has(PROPERTY_PATTERNS)) {
				List<Object> objList = dataJson.getJSONArray(PROPERTY_PATTERNS).toList();
				patterns = new ArrayList<String>();
				for (Object object : objList) {
				    patterns.add(object.toString());
				}
			}

			if (dataJson.has(PROPERTY_VAULT_REFERENCE)) {
				vaultReference = dataJson.getJSONObject(PROPERTY_VAULT_REFERENCE);
				if (vaultReference.has(PROPERTY_VAULT_REFERENCE_PATH) && vaultReference.has(PROPERTY_VAULT_REFERENCE_KEY)) {
					vaultReferencePath = vaultReference.getString(PROPERTY_VAULT_REFERENCE_PATH);
					vaultReferenceKey = vaultReference.getString(PROPERTY_VAULT_REFERENCE_KEY);
				}
			}

			// Extract issue/comment create action cloud event attributes
			if (dataJson.has(PROPERTY_PARENT))
				parent = dataJson.getString(PROPERTY_PARENT);
			if (dataJson.has(PROPERTY_REPO_NAME))
                repoName = dataJson.getString(PROPERTY_REPO_NAME);
			if (dataJson.has(PROPERTY_TITLE))
				title = dataJson.getString(PROPERTY_TITLE);	
            if (dataJson.has(PROPERTY_BODY))
				body = dataJson.getString(PROPERTY_BODY);	
			if (json.has(PROPERTY_ISSUE_TYPE))
				issueType = json.getString(PROPERTY_ISSUE_TYPE);
			if (dataJson.has(PROPERTY_ITEM_ID))
				itemId = dataJson.getInt(PROPERTY_ITEM_ID);		
			if (dataJson.has(PROPERTY_LABELS)) {
				List<Object> objList = dataJson.getJSONArray(PROPERTY_LABELS).toList();
				labels = new ArrayList<String>();
				for (Object object : objList) {
				    labels.add(object.toString());
				}
			}

			// Extract webhook action cloud event attributes
			if (dataJson.has(PROPERTY_WEBHOOK_ACTION))
				webhookAction = dataJson.getString(PROPERTY_WEBHOOK_ACTION);
			if (dataJson.has(PROPERTY_EVENTS)) {
				List<Object> objList = dataJson.getJSONArray(PROPERTY_EVENTS).toList();
				events = new ArrayList<GHEvent>();
				main : for (Object object : objList) {
					for (GHEvent g : GHEvent.values()) {
						if (g.name().equals(object.toString())) {
							events.add(g);
							continue main;
						}
					}
					logger.log(Level.SEVERE, "Configuration contains invalid event: " + object.toString() + ". Setting events to null.");
					events = null;
					break main;
				}

			}
			if (dataJson.has(PROPERTY_WEBHOOK_ID))
				webhookId = dataJson.getInt(PROPERTY_WEBHOOK_ID);
			if (dataJson.has(PROPERTY_WEBHOOK_URL))
				webhookURL = dataJson.getString(PROPERTY_WEBHOOK_URL);				
		}
		catch (Exception e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
		//logger.log(Level.INFO, toString()); 
	}
	
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(PROPERTY_CONNECTION_ID + ": " + connectionid + "\n");
		sb.append(PROPERTY_COMPONENT_NAME + ": " + componentname + "\n");

		sb.append(PROPERTY_LIVE + ": " + live + "\n");
		sb.append(PROPERTY_PATTERNS + ": " + target + "\n");
		sb.append(PROPERTY_URI + ": " + uri + "\n");
		// Do not print out the Token, it will cause security problems
		sb.append(PROPERTY_TOKEN + " is the token non-null?:" +  (token == null) + "\n");
		sb.append(PROPERTY_VAULT_REF + ": " + vaultRef + "\n");
		sb.append(PROPERTY_VAULT_REFERENCE + ": " + vaultReference + "\n");
		sb.append(PROPERTY_PATTERNS + ": " + patterns + "\n");

		sb.append(PROPERTY_PARENT + ": " + parent + "\n");
		sb.append(PROPERTY_REPO_NAME + ": " + repoName + "\n");
		sb.append(PROPERTY_TITLE + ": " + title + "\n");
		sb.append(PROPERTY_BODY + ": " + body + "\n");
		sb.append(PROPERTY_LABELS + ": " + labels + "\n");
		sb.append(PROPERTY_PARENT + ": " + parent + "\n");
		sb.append(PROPERTY_ITEM_ID + ": " + itemId + "\n");
		sb.append(PROPERTY_ISSUE_TYPE + ": " + issueType + "\n");

		sb.append(PROPERTY_WEBHOOK_ACTION + ": " + webhookAction + "\n");
		sb.append(PROPERTY_EVENTS + ": " + events + "\n");
		sb.append(PROPERTY_WEBHOOK_ID + ": " + webhookId + "\n");
		sb.append(PROPERTY_WEBHOOK_URL + ": " + webhookURL + "\n");
		
		return sb.toString();
	}

	public String getConnectionID() {
		return connectionid;
	}

	public String getComponentName() {
		return componentname;
	}
	
	// Connection create
	public Boolean getLive() {
		return live;
	}

	public Boolean getInsecureWebhook() {
		return insecureWebhook;
	}

	public void setInsecureWebhook(Boolean insecureWebhook) {
		this.insecureWebhook = insecureWebhook;
	}

	public void setLive(Boolean live) {
		this.live = live;
	}

	public List<String> getPatterns(){
		return patterns;
	}
	
	public void setPatterns(List<String> patterns) {
		this.patterns = patterns;
	}
	
	public String getTarget() {
		return target;
	}
	
	public void setTarget(String target) {
		this.target = target;
	}

	public String getToken() {
		return token;
	}
	
	public void setToken(String token) {
		this.token = token;
	}

	public String getVaultRef() {
		return vaultRef;
	}
	
	public void setVaultRef(String vaultRef) {
		this.vaultRef = vaultRef;
	}

	public String getVaultReferenceKey() {
		return vaultReferenceKey;
	}

	public void setVaultReferenceKey(String vaultReferenceKey) {
		this.vaultReferenceKey = vaultReferenceKey;
	}

	public String getVaultReferencePath() {
		return vaultReferencePath;
	}

	public void setVaultReferencePath(String vaultReferencePath) {
		this.vaultReferencePath = vaultReferencePath;
	}
	
	// URI or URL? Maybe rename to URL for the local variable
	public String getURL() {
		return uri;
	}
	
	public void setURL(String uri) {
		this.uri = uri;
	}


	// Issue/Comment create action
	public List<String> getLabels(){
		return labels;
	}
	
	public void setLabels(List<String> labels) {
		this.labels = labels;
	}
	
	public String getParent() {
		return parent;
	}
	
	public void setParent(String parent) {
		this.parent = parent;
	}

	public String getRepoName() {
		return repoName;
	}

	public void setRepoName(String repoName) {
		this.repoName = repoName;
	}

    public String getTitle() {
		return title;
	}

    public void setTitle(String title) {
		this.title = title;
	}

    public String getBody() {
		return body;
	}

    public void setBody(String body) {
		this.body = body;
	}

	public int getItemId() {
		return itemId;
	}

	public void setItemId(int itemId) {
		this.itemId = itemId;
	}

	public String getIssueType() {
		return issueType;
	}

	public void setIssueType(String issueType) {
		this.issueType = issueType;
	}

	public String getWebhookAction() {
		return webhookAction;
	}

	public void setWebhookAction(String webhookAction) {
		this.webhookAction = webhookAction;
	}

	public List<GHEvent> getEvents() {
		return events;
	}

	public void setEvents(List<GHEvent> events) {
		this.events = events;
	}

	public int getWebhookId() {
		return webhookId;
	}

	public void setWebhookId(int webhookId) {
		this.webhookId = webhookId;
	}

	public String getWebhookURL() {
		return webhookURL;
	}

	public void setWebhookURL(String webhookURL) {
		this.webhookURL = webhookURL;
	}
	
	// Allows for cloning of the configuration, so the exact state of the action and configuration can 
	// match up
	public Object clone() throws CloneNotSupportedException{
	    ConnectorConfiguration cloned = (ConnectorConfiguration) super.clone();
	    return cloned;
	}

	protected String getTokenDecodeCertLocation(){
		return tokenDecodeCertLocation;
	}

	protected void setTokenDecodeCertLocation(String tokenDecodeCertLocation){
		this.tokenDecodeCertLocation = tokenDecodeCertLocation;
	}
}