package com.ibm.watson.aiops.connectors.github;

import java.io.IOException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONObject;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHHook;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import io.cloudevents.CloudEvent;
import io.micrometer.core.instrument.Counter;

public class WebhookAction implements Runnable {

    ConnectorAction action;

    static final Logger logger = Logger.getLogger(WebhookAction.class.getName());

    private static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public WebhookAction(ConnectorAction action) {
        this.action = action;
    }

    protected String getTraceTimestamp() {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());

        return simpleDateFormat.format(timestamp);
    }

    @Override
    public void run() {

        logger.log(Level.INFO, "Run WebhookAction");
        Counter actionCounter = action.getActionCounter();
        Counter actionErrorCounter = action.getActionErrorCounter();
        actionCounter.increment();

        ConnectorConfiguration config = action.getConfiguration();
        GitHub github = action.getGitHub();

        Connector connector = action.getConnector();

        CloudEvent ce;

        if (!isConfigValid(config)) {
            logger.log(Level.SEVERE, "Webhook configuration is not valid");
            ce = connector.createEvent((System.nanoTime() - config.getStartTime()), ConnectorConstants.WEBHOOK_ACTION_FAILED, ConnectorConstants.CE_ACTION_FAILURE_MESSAGE);
            connector.emitCloudEvent(ConnectorConstants.ACTION_GITHUB_TOPIC, null, ce);
            
            actionErrorCounter.increment();
            return; 
        }

        try {
            String webhookAction = config.getWebhookAction();
            String target = config.getTarget();
            String orgName = getParent(target);
            String webhookSecret = connector.getWebhookSecret();
            String webhookURL = config.getWebhookURL();
            String connId = config.getConnectionID();
            
            String webhookName = "web";
            boolean active = true;
            Map<String, String> webhookConfig = new HashMap<String, String>();
            webhookConfig.put("url", webhookURL);
            webhookConfig.put("content_type", "json");
            webhookConfig.put("secret", getEncodedSecret(webhookSecret));

            List<GHHook> hooks;
            GHOrganization myOrg;
            GHRepository myRepo;
            
            if (webhookAction.equals(ConnectorConstants.WEBHOOK_CREATE_ACTION)) {
                final List<GHEvent> EVENTS = config.getEvents();

                if (getRepo(target) == null) {
                    myOrg = github.getOrganization(orgName);
                    hooks = myOrg.getHooks();
                    deleteHooksWithSameUrlorContainsConnId(hooks, webhookURL, connId, connector, config);
                    myOrg.createHook(webhookName, webhookConfig, EVENTS, active);
                } else {
                    myRepo = github.getRepository(orgName + "/" + getRepo(target));
                    hooks = myRepo.getHooks();
                    deleteHooksWithSameUrlorContainsConnId(hooks, webhookURL, connId, connector, config);
                    myRepo.createHook(webhookName, webhookConfig, EVENTS, active);
                }
                    
            } else {

                if (getRepo(target) == null) {
                    myOrg = github.getOrganization(orgName);
                    hooks = myOrg.getHooks();
                    deleteHooksWithSameUrlorContainsConnId(hooks, webhookURL, connId, connector, config);
                } else {
                    myRepo = github.getRepository(orgName + "/" + getRepo(target));
                    hooks = myRepo.getHooks();
                    deleteHooksWithSameUrlorContainsConnId(hooks, webhookURL, connId, connector, config);                  
                }

            }

            logger.log(Level.INFO, "Webhook " + webhookAction + " was successfully completed");
            logger.log(Level.INFO, "Emitting cloud event");
            String jsonString = webhookAction.equals(ConnectorConstants.WEBHOOK_CREATE_ACTION) ? 
                new JSONObject()
                    .put("live", config.getLive())
                    .put("webhook_url", config.getWebhookURL())
                    .put("target", config.getTarget())
                    .put("webhook_action", config.getWebhookAction())
                    .toString() : 
                new JSONObject()
                    .put("target", config.getTarget())
                    .put("webhook_action", config.getWebhookAction())
                    .put("webhook_id", config.getWebhookId())
                    .toString();
            ce = connector.createEvent((System.nanoTime() - config.getStartTime()), config.getWebhookAction().equals(ConnectorConstants.WEBHOOK_CREATE_ACTION) ? ConnectorConstants.WEBHOOK_CREATE_ACTION_COMPLETED : ConnectorConstants.WEBHOOK_DELETE_ACTION_COMPLETED, jsonString);
            connector.emitCloudEvent(ConnectorConstants.ACTION_GITHUB_TOPIC, null, ce);

        } catch (IOException e) {
            if (e.getMessage().contains("Hook already exists")) {
                logger.log(Level.INFO, "Webhook already exists, no further action");
            }
            else {                
                actionErrorCounter.increment();
                connector.triggerWebhookAlerts(config, e);
            }
        }
    }

    private void deleteHooksWithSameUrlorContainsConnId(List<GHHook> hooks, String webhookURL, String connId, Connector connector, ConnectorConfiguration config) {
        for (GHHook hook: hooks) {
            String url = hook.getConfig().get("url");
            if (url.equals(webhookURL) || url.contains(connId)) {
                try {
                    hook.delete();
                    logger.log(Level.INFO, "Webhook with id " + hook.getId() + " was successfully deleted");
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Fail to delete webhook with id " + hook.getId());
                    connector.triggerWebhookAlerts(config, e);
                }
            } 
        }
    }

    private boolean isConfigValid(ConnectorConfiguration config) {

        String configurationName = "Webhook";

        if (config.getTarget() == null || config.getTarget().equals("")) {
            logger.log(Level.SEVERE, Utils.getDataMissingPropertyErrorMessage(configurationName, ConnectorConfiguration.PROPERTY_TARGET));
            return false;
        }

        if (config.getWebhookAction() == null || config.getWebhookAction().equals("")) {
            logger.log(Level.SEVERE, Utils.getDataMissingPropertyErrorMessage(configurationName, ConnectorConfiguration.PROPERTY_WEBHOOK_ACTION));
            return false;
        }

        if (!config.getWebhookAction().equals(ConnectorConstants.WEBHOOK_CREATE_ACTION) && !config.getWebhookAction().equals(ConnectorConstants.WEBHOOK_DELETE_ACTION)) {
            logger.log(Level.SEVERE, ConnectorConfiguration.PROPERTY_WEBHOOK_ACTION + " must either be `" + ConnectorConstants.WEBHOOK_CREATE_ACTION + "` or `" + ConnectorConstants.WEBHOOK_DELETE_ACTION + "`");
            return false;
        }

        if (config.getWebhookAction().equals(ConnectorConstants.WEBHOOK_CREATE_ACTION)) {
            if (config.getEvents() == null || config.getEvents().isEmpty()) {
                logger.log(Level.SEVERE, Utils.getDataMissingPropertyErrorMessage(configurationName, ConnectorConfiguration.PROPERTY_EVENTS));
                return false;
            }
            if (config.getWebhookURL() == null || config.getWebhookURL().equals("")) {
                logger.log(Level.SEVERE, Utils.getDataMissingPropertyErrorMessage(configurationName, ConnectorConfiguration.PROPERTY_WEBHOOK_URL));
                return false;
            }
        }

        return true;
    }

    protected String getParent(String fullName) {
        if (fullName != null) {
            String[] parts = fullName.split("/");
            if (parts.length > 0) {
                return parts[0];
            }
        }
        return null;
    }

    protected String getRepo(String fullName) {
        if (fullName != null) {
            String[] parts = fullName.split("/");
            if (parts.length > 1) {
                return parts[1];
            }
        }
        return null;
    }

    public static String getEncodedSecret(String secret) {
        return Base64.getEncoder().encodeToString(new StringBuilder(secret).reverse().toString().getBytes());
    }
}