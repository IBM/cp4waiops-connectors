package com.ibm.watson.aiops.connectors.github;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONObject;
import org.kohsuke.github.GHIssueBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import io.cloudevents.CloudEvent;
import io.micrometer.core.instrument.Counter;

public class IssueCreateAction implements Runnable {

    ConnectorAction action;

    static final Logger logger = Logger.getLogger(IssueCreateAction.class.getName());

	public IssueCreateAction(ConnectorAction action) {
		this.action = action;
	}
    
    @Override
	public void run() {
        logger.log(Level.INFO,"Run IssueCreateAction");

        Counter actionCounter = action.getActionCounter();
        Counter actionErrorCounter = action.getActionErrorCounter();

        actionCounter.increment();

        ConnectorConfiguration config = action.getConfiguration();
        GitHub github = action.getGitHub();

        Connector connector = action.getConnector();

        CloudEvent ce = connector.createEvent((System.nanoTime() - config.getStartTime()), ConnectorConstants.CREATE_ISSUE_ACTION_FAILED, ConnectorConstants.CE_ACTION_FAILURE_MESSAGE);

        if (!isConfigValid(config)) {
            logger.log(Level.SEVERE, "Issue configuration is not valid");
            connector.emitCloudEvent(ConnectorConstants.ACTION_GITHUB_TOPIC, null, ce);
            actionErrorCounter.increment();
            return; 
        }
        
        try {
            logger.log(Level.INFO,"Process issue create action");
                
            String repo_path = config.getParent() + "/" + config.getRepoName();
            GHRepository myRepo = github.getRepository(repo_path);
            GHIssueBuilder builder = myRepo.createIssue(config.getTitle());
            builder.body(config.getBody());
            if (config.getLabels() != null) {
                for (String label : config.getLabels()) {
                    builder.label(label);
                }
            }
            builder.create();
            logger.log(Level.INFO,"Issue was successfully created");

            logger.log(Level.INFO,"Emit cloud event");
            String jsonString = new JSONObject()
                .put("parent", config.getParent())
                .put("repo_name", config.getRepoName())
                .put("title", config.getTitle())
                .put("body", config.getBody())
                .toString();
            
            ce = connector.createEvent((System.nanoTime() - config.getStartTime()), ConnectorConstants.CREATE_ISSUE_ACTION_COMPLETED, jsonString);
            // TODO: produce to different topic
            connector.emitCloudEvent(ConnectorConstants.ACTION_GITHUB_TOPIC, null, ce);

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Issue was NOT successfully created.");
            connector.emitCloudEvent(ConnectorConstants.ACTION_GITHUB_TOPIC, null, ce);
            actionErrorCounter.increment();
            connector.triggerCommonAlerts(config, e);
        }
    }

    private boolean isConfigValid(ConnectorConfiguration config) {

        String configurationName = "Issue";

        if (config.getParent() == null || config.getParent().equals("")) {
            logger.log(Level.SEVERE, Utils.getDataMissingPropertyErrorMessage(configurationName, ConnectorConfiguration.PROPERTY_PARENT));
            return false;
        }

        if (config.getRepoName() == null || config.getRepoName().equals("")) {
            logger.log(Level.SEVERE, Utils.getDataMissingPropertyErrorMessage(configurationName, ConnectorConfiguration.PROPERTY_REPO_NAME));
            return false;
        }

        if (config.getTitle() == null || config.getTitle().equals("")) {
            logger.log(Level.SEVERE, Utils.getDataMissingPropertyErrorMessage(configurationName, ConnectorConfiguration.PROPERTY_TITLE));
            return false;
        }

        // TODO: Should this be a required field?
        if (config.getBody() == null || config.getBody().equals("")) {
            logger.log(Level.SEVERE, Utils.getDataMissingPropertyErrorMessage(configurationName, ConnectorConfiguration.PROPERTY_BODY));
            return false;
        }

        return true;
    }
}
