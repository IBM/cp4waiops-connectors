package com.ibm.watson.aiops.connectors.github;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONObject;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import io.cloudevents.CloudEvent;
import io.micrometer.core.instrument.Counter;

public class CommentCreateAction implements Runnable {
    
	ConnectorAction action;

    static final Logger logger = Logger.getLogger(CommentCreateAction.class.getName());

    private final String ISSUE_TYPE = "issue";
    private final String PR_TYPE = "pullRequest";

	public CommentCreateAction(ConnectorAction action) {
		this.action = action;
	}
	
	@Override
	public void run() {
		logger.log(Level.INFO, "Run CommentCreateAction");

        Counter actionCounter = action.getActionCounter();
        Counter actionErrorCounter = action.getActionErrorCounter();

        actionCounter.increment();

        ConnectorConfiguration config = action.getConfiguration();
        GitHub github = action.getGitHub();

        Connector connector = action.getConnector();

        CloudEvent ce = connector.createEvent((System.nanoTime() - config.getStartTime()), ConnectorConstants.CREATE_COMMENT_ACTION_FAILED, ConnectorConstants.CE_ACTION_FAILURE_MESSAGE);

        if (!isConfigValid(config)) {
            logger.log(Level.SEVERE, "Comment configuration is not valid");
            connector.emitCloudEvent(ConnectorConstants.ACTION_GITHUB_TOPIC, null, ce);
            actionErrorCounter.increment();
            return; 
        }

        try {
            logger.log(Level.INFO,"Process comment create action");

            String path = config.getParent() + "/" + config.getRepoName();
            GHRepository myRepo = github.getRepository(path);
            
            String issueType = config.getIssueType();
    
            if (issueType.equals(ISSUE_TYPE)) {
                myRepo.getIssue(config.getItemId()).comment(config.getBody());
            }
            else  {
                myRepo.getPullRequest(config.getItemId()).comment(config.getBody());
            }

            logger.log(Level.INFO, "Comment was successfully created");

            logger.log(Level.INFO,"Emit cloud event");
            String jsonString = new JSONObject()
                .put("parent", config.getParent())
                .put("repo_name", config.getRepoName())
                .put("item_id", config.getItemId())
                .put("body", config.getBody())
                .put("issuetype", config.getIssueType())                
                .toString();

            ce = connector.createEvent((System.nanoTime() - config.getStartTime()), ConnectorConstants.CREATE_COMMENT_ACTION_COMPLETED, jsonString);
            connector.emitCloudEvent(ConnectorConstants.ACTION_GITHUB_TOPIC, null, ce);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Comment was NOT successfully created.");
            connector.emitCloudEvent(ConnectorConstants.ACTION_GITHUB_TOPIC, null, ce);
            actionErrorCounter.increment();
            connector.triggerCommonAlerts(config, e);
        }
		
	}

    private boolean isConfigValid(ConnectorConfiguration config) {

        String configurationName = "Comment";

        if (config.getParent() == null || config.getParent().equals("")) {
            logger.log(Level.SEVERE, Utils.getDataMissingPropertyErrorMessage(configurationName, ConnectorConfiguration.PROPERTY_PARENT));
            return false;
        }

        if (config.getRepoName() == null || config.getRepoName().equals("")) {
            logger.log(Level.SEVERE, Utils.getDataMissingPropertyErrorMessage(configurationName, ConnectorConfiguration.PROPERTY_REPO_NAME));
            return false;
        }

        if (config.getItemId() == ConnectorConfiguration.DEFAULT_ID) {
            logger.log(Level.SEVERE, Utils.getDataMissingPropertyErrorMessage(configurationName, ConnectorConfiguration.PROPERTY_ITEM_ID));
            return false;
        }

        if (config.getIssueType() == null || config.getIssueType().equals("")) {
            logger.log(Level.SEVERE, Utils.getDataMissingPropertyErrorMessage(configurationName, ConnectorConfiguration.PROPERTY_ISSUE_TYPE));
            return false;
        }

        if (!config.getIssueType().equals(PR_TYPE) && !config.getIssueType().equals(ISSUE_TYPE)) {
            logger.log(Level.SEVERE, ConnectorConfiguration.PROPERTY_ISSUE_TYPE + " must either be `" + ISSUE_TYPE + "` or `" + PR_TYPE + "`");
            return false;
        }

        if (config.getBody() == null || config.getBody().equals("")) {
            logger.log(Level.SEVERE, Utils.getDataMissingPropertyErrorMessage(configurationName, ConnectorConfiguration.PROPERTY_BODY));
            return false;
        }

        return true;
    }
}
