package com.ibm.watson.aiops.connectors.github;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ibm.aiops.connectors.bridge.ConnectorStatus;
import com.ibm.cp4waiops.connectors.sdk.ConnectorBase;
import com.ibm.cp4waiops.connectors.sdk.ConnectorException;
import com.ibm.cp4waiops.connectors.sdk.Constant;
import com.ibm.cp4waiops.connectors.sdk.EventLifeCycleEvent;
import com.ibm.cp4waiops.connectors.sdk.SDKSettings;
import com.ibm.cp4waiops.connectors.sdk.StatusWriter;
import com.ibm.cp4waiops.connectors.sdk.VaultHelper;
import com.ibm.cp4waiops.connectors.sdk.Util;

import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.concurrent.BlockingQueue;

public class Connector extends ConnectorBase {
    static final Logger logger = Logger.getLogger(Connector.class.getName());
    
    static final String ADDRESS_CE = "http://example.com/myaddress";

    // Self identifier
    static final URI SELF_SOURCE = URI.create("template.connectors.aiops.watson.ibm.com/connectorgithub");

    private AtomicReference<GitHub> github = new AtomicReference<>();

    protected ConcurrentLinkedQueue<ConnectorAction> actionQueue = new ConcurrentLinkedQueue<ConnectorAction>(); 
        
    protected ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);
    
    private AtomicReference<ConnectorConfiguration> _configuration = new AtomicReference<>();
    private AtomicBoolean _configured = new AtomicBoolean(false);
    private Counter _testsPerformed;
    private Counter _listReposActionCounter;
    private Counter _listReposActionErrorCounter;
    private Counter _createIssueActionCounter;
    private Counter _createIssueActionErrorCounter;
    private Counter _createCommentActionCounter;
    private Counter _createCommentActionErrorCounter;
    private Counter _webhookActionCounter;
    private Counter _webhookActionErrorCounter;

    private final String HOST_PREFIX = "github";

    private WebhookServer webhookServer;

    private String webhookSecret;

    private boolean isLiveDataOn = false;

    protected int sleepInterval = 1;

    // Status checks
    private AtomicBoolean statusValidCredentials = new AtomicBoolean(false);

    // For unit testing, anonymous public GitHub is used. Normally, the user
    // cannot do anonymous GitHub login
    private boolean checkCredentialsDuringClientInit = true;

    @Override 
    public void onInit(String connectorID, String componentName, BlockingQueue<CloudEvent> eventOutput, StatusWriter statusWriter, VaultHelper vaultHelper) throws ConnectorException {
        super.onInit(connectorID, componentName, eventOutput, statusWriter, vaultHelper);
        webhookServer = new WebhookServer(this, getConnectorID());

        statusValidCredentials.set(false);
        new Thread(webhookServer).start();
    }

    @Override
    public void onDestroy() throws ConnectorException {
        webhookServer.stop();
    }

    @Override
    public void registerMetrics(MeterRegistry metricRegistry) {
        super.registerMetrics(metricRegistry);

        _testsPerformed = metricRegistry.counter("connector.tests.performed");
        _listReposActionCounter = metricRegistry.counter(ConnectorConstants.METRICS_LIST_REPO_ACTION);
        _listReposActionErrorCounter = metricRegistry.counter(ConnectorConstants.METRICS_LIST_REPO_ACTION_ERROR);
        _createIssueActionCounter = metricRegistry.counter("github.create.issue.action");
        _createIssueActionErrorCounter = metricRegistry.counter("github.create.issue.action.error");
        _createCommentActionCounter = metricRegistry.counter("github.create.comment.action");
        _createCommentActionErrorCounter = metricRegistry.counter("github.create.comment.action.error");
        _webhookActionCounter = metricRegistry.counter("github.webhook.action");
        _webhookActionErrorCounter = metricRegistry.counter("github.webhook.action.error");
    }    

    private String constructWebhookUrl(ConnectorConfiguration config) {
        logger.log(Level.INFO, "constructWebhookUrl");
        String webhookUrl = System.getenv("WEBHOOK_URL");
        if (webhookUrl != null) {
            return webhookUrl;
        }
        logger.log(Level.INFO, "No env var found for the webhook URL. Build the URL");
 
        String HTTPS_PROTOCOL = "https://";
        String HTTP_PROTOCOL = "http://";

        // Retrieve values from config map and construct webhookurl
        String baseUrl = System.getenv("baseUrl");
        String namespace = System.getenv("namespace");
        if (baseUrl != null && namespace != null) {
            String protocol = config.getInsecureWebhook() ? HTTP_PROTOCOL : HTTPS_PROTOCOL;
            webhookUrl = protocol + HOST_PREFIX + "-" + getConnectorID() + "-" + namespace + "." + baseUrl;
        }
        logger.log(Level.INFO, "webhookUrl :" + webhookUrl);
        return webhookUrl;
    }

    @Override
    public SDKSettings onConfigure(CloudEvent event) throws ConnectorException {
    	
    	logger.log(Level.INFO, "onConfigure");
    	
    	ConnectorConfiguration connectionCreateCfg = new ConnectorConfiguration();
    	connectionCreateCfg.loadDataFromJson(Util.convertCloudEventToJSON(event));

        isLiveDataOn = connectionCreateCfg.getLive();

        ConnectorConfiguration oldConfig = _configuration.get();
        buildGithubClient(oldConfig, connectionCreateCfg);

        boolean hasCoreConfigChanged = hasCoreConfigChanged(oldConfig, connectionCreateCfg);

        boolean hasRepoListConfigChanged = hasRepoListConfigChanged(oldConfig, connectionCreateCfg);

        if (!_configured.get() || hasCoreConfigChanged || hasRepoListConfigChanged) {

            ConnectorAction repoAction = new ConnectorAction(ConnectorConstants.ACTION_LIST_REPOS, connectionCreateCfg, github.get(), this, _listReposActionCounter, _listReposActionErrorCounter);
            addActionToQueue(repoAction);

            final List<GHEvent> EVENTS = Arrays.asList(GHEvent.ISSUES, GHEvent.ISSUE_COMMENT, GHEvent.PULL_REQUEST, GHEvent.PULL_REQUEST_REVIEW, GHEvent.PULL_REQUEST_REVIEW_COMMENT, GHEvent.PUSH, GHEvent.REPOSITORY);
            connectionCreateCfg.setEvents(EVENTS);
            
            String webhookURL = constructWebhookUrl(connectionCreateCfg);

            if (webhookURL != null && (hasWebHookConfigChanged(oldConfig, connectionCreateCfg) || hasCoreConfigChanged)) {
                webhookURL += "/webhook/" + getConnectorID();
                connectionCreateCfg.setWebhookURL(webhookURL);
                webhookSecret = genSecret();

                // Webhook create is done regardless of live being on or not. The webhook will always exist
                // but processing the data is determined by the live
                connectionCreateCfg.setWebhookAction(ConnectorConstants.WEBHOOK_CREATE_ACTION);

                ConnectorAction webhookAction = new ConnectorAction(ConnectorConstants.WEBHOOK_ACTION, connectionCreateCfg, github.get(), this, _webhookActionCounter, _webhookActionErrorCounter);            
                addActionToQueue(webhookAction);
            }

            _configured.set(true);

            // Refresh the status on a configuration change
            updateStatusAndGetIfRunningState();
        } 

        // Update succeeded, store accepted configuration
        _configuration.set(connectionCreateCfg);
    	
        // Return topics
        SDKSettings settings = new SDKSettings();
        settings.consumeTopicNames = new String[] { ConnectorConstants.ACTION_GITHUB_TOPIC };
        // Note: ACTION_1_TOPIC is consumed from only for demonstration purposes, avoid
        // consuming from the same topic produced to in real connectors!
        settings.produceTopicNames = new String[] {ConnectorConstants.OUTPUT_REPO_TOPIC, ConnectorConstants.OUTPUT_ISSUE_TOPIC,
                ConnectorConstants.TOPIC_INPUT_LIFECYCLE_EVENTS };
        
        return settings;
    }

    @Override
    public SDKSettings onReconfigure(CloudEvent event) throws ConnectorException {
        // Update topics and local state if needed

        return onConfigure(event);
    }

    @Override
    public void onTerminate(CloudEvent event) {
        // Cleanup external resources

        ConnectorConfiguration actionCfg;

        actionCfg = new ConnectorConfiguration();
        actionCfg.loadDataFromJson(Util.convertCloudEventToJSON(event));
        actionCfg.setWebhookAction(ConnectorConstants.WEBHOOK_DELETE_ACTION);
        ConnectorAction webhookAction = new ConnectorAction(ConnectorConstants.WEBHOOK_ACTION, actionCfg, github.get(), this, _webhookActionCounter, _webhookActionErrorCounter);            
        WebhookAction wa = new WebhookAction(webhookAction);
        wa.run();
    }

    @Override
    public void onAction(String channelName, CloudEvent event) {
        ConnectorConfiguration actionCfg;

        switch (event.getType()) {
        case ConnectorConstants.CREATE_ISSUE_ACTION:
            actionCfg = new ConnectorConfiguration();
            actionCfg.loadDataFromJson(Util.convertCloudEventToJSON(event));
            ConnectorAction issueCreateAction = new ConnectorAction(ConnectorConstants.CREATE_ISSUE_ACTION, actionCfg, github.get(), this, _createIssueActionCounter, _createIssueActionErrorCounter);
            addActionToQueue(issueCreateAction);
            break;
        case ConnectorConstants.CREATE_COMMENT_ACTION:
            actionCfg = new ConnectorConfiguration();
            actionCfg.loadDataFromJson(Util.convertCloudEventToJSON(event));
            ConnectorAction commentCreateAction = new ConnectorAction(ConnectorConstants.CREATE_COMMENT_ACTION, actionCfg, github.get(), this, _createCommentActionCounter, _createCommentActionErrorCounter);
            addActionToQueue(commentCreateAction);
            break;               
        default:
            break;
        }
    }

    @Override
    public void run() {
        boolean interrupted = false;

        long lastStatusUpdate = 0;
        while (!interrupted) {
            try {   	
                // Process next action
                processActionFromActionQueue();

                // Periodic status update
                if (System.nanoTime() - lastStatusUpdate > ConnectorConstants.STATUS_RETRY_DURATION.toNanos()) {
                    lastStatusUpdate = System.nanoTime();
                    updateStatusAndGetIfRunningState();
                }
            	
            	// Can remove this sleep after...
            	Thread.sleep(100);
            	
            } catch (InterruptedException ignored) {
                // termination of the process has been requested
                interrupted = true;
                Thread.currentThread().interrupt();
            }
        }
    }

    protected boolean updateStatusAndGetIfRunningState(){
        // TODO add the webhook scenario
        if (statusValidCredentials.get()){
            emitRunningStatus();
        }
        else {
            emitBadCredentialStatus();
            return false;
        }

        return true;
    }

    public void triggerWebhookAlerts(ConnectorConfiguration config, Exception e) {
        CloudEvent ce;
        String errorMsg = "";
        if (e.getMessage() != null) {
            errorMsg = e.getMessage();
        }
        if (errorMsg.contains("Not Found")) {
            try {
                ce = createAlertEvent(config, ConnectorConstants.INSTANCE_UNAUTHORIZED_CE_TYPE,
                        EventLifeCycleEvent.EVENT_TYPE_PROBLEM);
                emitCloudEvent(ConnectorConstants.TOPIC_INPUT_LIFECYCLE_EVENTS, getPartition(), ce);
                logger.log(Level.SEVERE, "Alert created: Token Not Authenticated, sleeping for " + sleepInterval + " minute");
                TimeUnit.MINUTES.sleep(sleepInterval);
            } catch (JsonProcessingException | InterruptedException e1) {
                logger.log(Level.SEVERE, e1.getMessage(), e1);
            }
        } else {
            try {
                String alertType = config.getWebhookAction().equals(ConnectorConstants.WEBHOOK_CREATE_ACTION) ? ConnectorConstants.WEBHOOK_CREATE_FAILURE_CE_TYPE : ConnectorConstants.WEBHOOK_DELETE_FAILURE_CE_TYPE;                
                ce = createAlertEvent(config, alertType, EventLifeCycleEvent.EVENT_TYPE_PROBLEM);
                emitCloudEvent(ConnectorConstants.TOPIC_INPUT_LIFECYCLE_EVENTS, getPartition(), ce);
                logger.log(Level.SEVERE, "Webhook action was NOT successfully completed. Error: " + e.getMessage(), e);
                logger.log(Level.SEVERE, "Alert created: Webhook failure, sleeping for " + sleepInterval + " minute");            
                TimeUnit.MINUTES.sleep(sleepInterval);
            } catch (JsonProcessingException | InterruptedException e1) {
                logger.log(Level.SEVERE, e1.getMessage(), e1);
            }
        } 

    }

    public void triggerListRepoAlert(ConnectorConfiguration config) {
        CloudEvent ce;        

        try {
            ce = createAlertEvent(config, ConnectorConstants.INSTANCE_ZERO_RESULTS_CE_TYPE,
                    EventLifeCycleEvent.EVENT_TYPE_PROBLEM);
            emitCloudEvent(ConnectorConstants.TOPIC_INPUT_LIFECYCLE_EVENTS, getPartition(), ce);
            logger.log(Level.SEVERE, "Alert created: Zero repositories found");            
        } catch (JsonProcessingException e1) {
            logger.log(Level.SEVERE, e1.getMessage(), e1);
        }


    }

    public void triggerCommonAlerts(ConnectorConfiguration config, Exception e) {
        CloudEvent ce;
        String errorMsg = "";
        if (e.getMessage() != null) {
            errorMsg = e.getMessage();
        }
        if (errorMsg.contains(ConnectorConstants.STATUS_MESSAGE_BAD_CREDENTIALS)) {
            try {
                ce = createAlertEvent(config, ConnectorConstants.INSTANCE_BAD_CREDENTIALS_CE_TYPE,
                        EventLifeCycleEvent.EVENT_TYPE_PROBLEM);
                emitCloudEvent(ConnectorConstants.TOPIC_INPUT_LIFECYCLE_EVENTS, getPartition(), ce);
                logger.log(Level.SEVERE,
                        "General Alert created: Token Not Authenticated, sleeping for " + sleepInterval + " minute");
                TimeUnit.MINUTES.sleep(sleepInterval);
            } catch (JsonProcessingException | InterruptedException e1) {
                logger.log(Level.SEVERE, e1.getMessage(), e1);
            }
        } else {
            logger.log(Level.SEVERE, "Unhandled error caught: " + errorMsg, e);
        }

    }
    
    public CloudEvent createEvent(long responseTime, String ce_type, String jsonMessage) {
        // Structured content mode ensuress the Cloud Event headers are also included into the 	
        // Kafka messsage. Without it, only the data portion shows up in the message. Currently,	
        // the GitHub topology expects the Cloud Event headers to be in the messsage or it will	
        // have validation errors.
    	return CloudEventBuilder.v1()
    			.withId(UUID.randomUUID().toString())
    			.withSource(SELF_SOURCE)
                .withTime(OffsetDateTime.now())
                .withType(ce_type)
                .withDataContentType("application/json")
                .withExtension(ConnectorConstants.RESPONSE_TIME_CE_EXT, responseTime)
                .withExtension(CONNECTION_ID_CE_EXTENSION_NAME, getConnectorID())
                .withExtension(COMPONENT_NAME_CE_EXTENSION_NAME, getComponentName())
                .withExtension(ConnectorConstants.TOOL_TYPE_CE_EXT,ConnectorConstants.TOOL_TYPE_CE_EXT_VALUE)
                .withExtension(ConnectorConstants.CE_EXT_STRUCTURED_CONTENT_MODE, ConnectorConstants.CE_EXT_STRUCTURED_CONTENT_MODE_VALUE)                
                .withData(jsonMessage.getBytes()).build();
    }

    public CloudEvent createAlertEvent(ConnectorConfiguration config, String alertType, String eventType)
            throws JsonProcessingException {
        EventLifeCycleEvent elcEvent = newInstanceAlertEvent(config, alertType, eventType);
        return CloudEventBuilder.v1().withId(elcEvent.getId()).withSource(SELF_SOURCE).withType(alertType)
                .withExtension(TENANTID_TYPE_CE_EXTENSION_NAME, Constant.STANDARD_TENANT_ID)
                .withExtension(CONNECTION_ID_CE_EXTENSION_NAME, getConnectorID())
                .withExtension(COMPONENT_NAME_CE_EXTENSION_NAME, getComponentName())
                .withData(Constant.JSON_CONTENT_TYPE, elcEvent.toJSON().getBytes(StandardCharsets.UTF_8)).build();
    }

    protected static EventLifeCycleEvent newInstanceAlertEvent(ConnectorConfiguration config, String alertType,
            String eventType) {

        EventLifeCycleEvent event = new EventLifeCycleEvent();
        EventLifeCycleEvent.Type type = new EventLifeCycleEvent.Type();
        Map<String, String> details = new HashMap<>();

        Map<String, Object> sender = new HashMap<>();
        sender.put(EventLifeCycleEvent.RESOURCE_TYPE_FIELD, "GitHub connection");
        sender.put(EventLifeCycleEvent.RESOURCE_NAME_FIELD, config.getComponentName());
        sender.put(EventLifeCycleEvent.RESOURCE_SOURCE_ID_FIELD, config.getConnectionID());
        event.setSender(sender);

        Map<String, Object> resource = new HashMap<>();
        resource.put(EventLifeCycleEvent.RESOURCE_TYPE_FIELD, "GitHub connection");
        resource.put(EventLifeCycleEvent.RESOURCE_NAME_FIELD, config.getComponentName());
        resource.put(EventLifeCycleEvent.RESOURCE_SOURCE_ID_FIELD, config.getConnectionID());
        event.setResource(resource);

        event.setId(UUID.randomUUID().toString());
        event.setOccurrenceTime(Date.from(Instant.now()));
        event.setSeverity(3);
        event.setExpirySeconds(0);

        type.setEventType(eventType);
        type.setClassification("Monitoring issue");

        if (alertType == ConnectorConstants.INSTANCE_BAD_CREDENTIALS_CE_TYPE) {
            event.setSummary("GitHub Credentials Not Authenticated");
            type.setCondition("GitHub credentials are invalid");
            details.put("guidance", "Update the provided credentials");
        } else if (alertType == ConnectorConstants.INSTANCE_UNAUTHORIZED_CE_TYPE) {
            event.setSummary("GitHub Credentials Not Authorized ");
            type.setCondition("GitHub credentials do not have access to webhooks");
            details.put("guidance", "Update the credentials provided to one with proper organization access");
        } else if (alertType == ConnectorConstants.WEBHOOK_CREATE_FAILURE_CE_TYPE) {
            event.setSummary("GitHub Webhook Create Failed");
            type.setCondition("GitHub webhook create failed");
            details.put("guidance", "Ensure a valid target and proper admin access");
        } else if (alertType == ConnectorConstants.WEBHOOK_DELETE_FAILURE_CE_TYPE) {
            event.setSummary("GitHub Webhook Delete Failed");
            type.setCondition("GitHub webhook delete failed");
            details.put("guidance", "Ensure a valid target and proper admin access");
        }else if (alertType== ConnectorConstants.INSTANCE_ZERO_RESULTS_CE_TYPE) {
            event.setSummary("Zero GitHub Repositories Found");
            type.setCondition("GitHub connector found zero repositories");
            details.put("guidance", "Ensure the pattern provided is valid");
        }

        event.setType(type);
        event.setDetails(details);

        return event;
    }

    private void buildGithubClient(ConnectorConfiguration oldConfig, ConnectorConfiguration newConfig) throws ConnectorException {
        // Skip if no change was made
        if (github.get() != null && oldConfig != null && stringsEqual(oldConfig.getURL(), newConfig.getURL()) && stringsEqual(oldConfig.getToken(), newConfig.getToken()) 
            && stringsEqual(oldConfig.getVaultRef(), newConfig.getVaultRef()) && stringsEqual(oldConfig.getVaultReferenceKey(), newConfig.getVaultReferenceKey()) && stringsEqual(oldConfig.getVaultReferencePath(), newConfig.getVaultReferencePath())) { //TODO: check if token is updated in vault
            return;
        }

        String githubToken = newConfig.getToken();
        String vaultRef = newConfig.getVaultRef();
        String vaultReferenceKey = newConfig.getVaultReferenceKey();
        String vaultReferencePath = newConfig.getVaultReferencePath();

        if (vaultReferenceKey != null && vaultReferencePath != null && !vaultReferenceKey.isEmpty() && !vaultReferencePath.isEmpty()) {
            try {
                githubToken = lookupVaultValue(vaultReferencePath, vaultReferenceKey);
                logger.log(Level.INFO, "Using vault reference");
            } catch (Exception error) {
                throw new ConnectorException("Failed to retreive github token from vault", error);
            }
        } else if (vaultRef != null && !vaultRef.isEmpty()) {
            try {
                String path = "/ibm-vault-access/data/" + vaultRef;
                String keyName = "accessToken";
                githubToken = lookupVaultValue(path, keyName);
                logger.log(Level.INFO, "Using vault ref");
            } catch (Exception error) {
                throw new ConnectorException("Failed to retreive github token from vault", error);
            }
        }

        if (githubToken == null || githubToken.isEmpty()) {
            throw new ConnectorException("Failed to retrieve github");
        }

        // Build client
        try {
            statusValidCredentials.set(false);
            GitHub client = new GitHubBuilder()
                .withEndpoint(CommonActionUtilities.getEndPointURL(newConfig.getURL()))
                .withOAuthToken(githubToken)
                .build();
            github.set(client);

            // Creating the client does not throw a credential error
            if (checkCredentialsDuringClientInit){
                boolean isCredsValid = client.isCredentialValid();
                logger.log(Level.INFO, "Is credential valid check: " + isCredsValid);
                if (!isCredsValid){
                    emitBadCredentialStatus();                                        
                    ConnectorException e = new ConnectorException(ConnectorConstants.STATUS_MESSAGE_BAD_CREDENTIALS);
                    triggerCommonAlerts(newConfig, e);
                    throw e;
                }
            }

            logger.log(Level.INFO, "Github client built successfully");
            statusValidCredentials.set(true);
            // Need to reset the state to running
            updateStatusAndGetIfRunningState();
        } catch(IOException error) {

            logger.log(Level.SEVERE, "Failed to create GitHub client", error);

            throw new ConnectorException("Failed to create github client", error);
        }
    }

    private boolean stringsEqual(String a, String b) {
        if (a == null || b == null) return a == b;
        return a.equals(b);
    }
    
    // In the future this may be dynamically generated, so use a method to get the value
    protected String getEventAddress() {
    	return ADDRESS_CE;
    }

    // A helper function that checks github instantiation before adding action to queue
    private void addActionToQueue(ConnectorAction action) {
        if (action.getGitHub() != null) {
            actionQueue.add(action);
            logger.log(Level.INFO, "Action was successfully added");
        } else {
            logger.log(Level.SEVERE, "Action was NOT successfully added because github was not instantiated");
        }
    }

    // A helper function that generates a secret
    public String genSecret() {
        final char[] aplhaNum = "0123456789abcdefghijklmnopqrstuvwxyz-_ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
        Random rand = new SecureRandom();
        StringBuilder secret = new StringBuilder(); 
        for (int i = 0; i < 20; i++) {
            secret.append(aplhaNum[rand.nextInt(aplhaNum.length)]);
        }
        return secret.toString();
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public Boolean isLiveDataOn() {
        logger.log(Level.INFO, "Live data on: " + isLiveDataOn);
        return isLiveDataOn;
    }

    protected void processActionFromActionQueue(){
        // Process next action
        ConnectorAction currentAction = actionQueue.poll();
        if (currentAction != null) {
            logger.log(Level.INFO, "processActionFromActionQueue: " + currentAction.toString());
            executor.execute(ConnectorActionFactory.getRunnableAction(currentAction));
        }
    }

    protected void setGitHub(GitHub client){
        if(client != null){
            github.set(client);
        }
    }

    protected Counter getListReposActionCounter(){
        return _listReposActionCounter;
    }

    protected Counter getListReposActionErrorCounter(){
        return _listReposActionErrorCounter;
    }

    // Disabling credential checks for buildGithubClient should only be done for unit testing
    // where anonymous GitHub connection is done
    protected void setCheckCredentialsDuringClientInit(boolean checkCredentialsDuringClientInit){
        this.checkCredentialsDuringClientInit = checkCredentialsDuringClientInit;
    }

    protected boolean getCheckCredentialsDuringClientInit(){
        return checkCredentialsDuringClientInit;
    }

    protected void emitBadCredentialStatus(){
        Map<String, String> extraProps = new HashMap<String, String>();
        extraProps.put(ConnectorConstants.STATUS_KEY_CREDENTIALS, ConnectorConstants.STATUS_MESSAGE_BAD_CREDENTIALS);

        // If the credentials are invalid, set the phase to Errored. It's much more likely that the user
        // entered the wrong credentials, so retrying will likely not change the results. If the user
        // changes the credentials, the client will be built again
        emitStatus(ConnectorStatus.Phase.Errored, ConnectorConstants.STATUS_RETRY_DURATION, extraProps);
    }

    protected void emitRunningStatus(){
        emitStatus(ConnectorStatus.Phase.Running, ConnectorConstants.STATUS_RETRY_DURATION);
    }

    // The core configuration is the URL and credentials. This would require fundamental
    // actions like webhooks and repo list to be redone
    protected boolean hasCoreConfigChanged(ConnectorConfiguration oldConfig,
            ConnectorConfiguration newConfig) {
        if (oldConfig != null && newConfig != null) {
            // Token or URL has changed
            if (stringsEqual(oldConfig.getToken(), newConfig.getToken())
            && stringsEqual(oldConfig.getURL(), newConfig.getURL())
            && oldConfig.getInsecureWebhook() == newConfig.getInsecureWebhook()
            && stringsEqual(oldConfig.getVaultRef(), newConfig.getVaultRef())
            && stringsEqual(oldConfig.getVaultReferenceKey(), newConfig.getVaultReferenceKey())
            && stringsEqual(oldConfig.getVaultReferencePath(), newConfig.getVaultReferencePath())
            ){
                return false;
            }
        }
        return true;
    }

    // This method should be called after hasCoreConfigChanged for some
    // repo list specific checks
    protected boolean hasRepoListConfigChanged(ConnectorConfiguration oldConfig,
            ConnectorConfiguration newConfig) {
        if (oldConfig != null && newConfig != null) {
            List<String> oldPatterns = oldConfig.getPatterns();
            List<String> newPatterns = newConfig.getPatterns();
            if (oldPatterns != null){
                // Old pattern was not null but new pattern is null
                if (newPatterns == null){
                    return true;
                }
                // The size is different
                if (oldPatterns.size() != newPatterns.size()){
                    return true;
                }

                ListIterator<String> oldIterator
                = oldPatterns.listIterator();

                // If the size is different and at least one key doesn't match
                while (oldIterator.hasNext()) {
                    String pattern = oldIterator.next();
                    if (!newPatterns.contains(pattern)){
                        return true;
                    }
                }  
            }
            else {
                // If the old pttern was null and the new pattern is not
                if (newPatterns != null)
                    return true;
            }
        }
        return false;
    }

    // This method should be called after hasCoreConfigChanged for webhook
    // specific checks
    protected boolean hasWebHookConfigChanged(ConnectorConfiguration oldConfig,
            ConnectorConfiguration newConfig) {
        // Only a change in target matters. Things like the pattern does not affect
        // the webhook
        if (oldConfig != null && newConfig != null) {
            if (!stringsEqual(oldConfig.getTarget(), newConfig.getTarget())){
                return true;
            }
            if (oldConfig.getLive() != newConfig.getLive()){
                return true;
            }
        }
        return false;
    }

    protected ConnectorConfiguration getConnectorConfiguration(){
        return _configuration.get();
    }

    protected String getPartition() {
        // Generate the partition
        ConnectorConfiguration currentConfig = getConnectorConfiguration();
        if (currentConfig != null) {
            String connectionID = currentConfig.getConnectionID();
            if (connectionID != null && !connectionID.isEmpty()) {
                return "{\"ce-partitionkey\":\"" + connectionID + "\"}";
            }
        }

        // If a partition cannot be created, return null
        // Null is a valid partition and will not throw errors, but
        // can run into unintended consequences from consumerss
        return null;
    }

    public int getSleepInterval() {
        return sleepInterval;
    }

    public void setSleepInterval(int sleepInterval) {
        this.sleepInterval = sleepInterval;
    }
}
