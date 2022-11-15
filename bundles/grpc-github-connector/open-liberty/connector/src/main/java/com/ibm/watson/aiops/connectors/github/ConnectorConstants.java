package com.ibm.watson.aiops.connectors.github;

import java.net.URI;

import java.time.Duration;

public class ConnectorConstants {
    public final static String ACTION_DUMMY = "dummy";

    // list repo action types
	public final static String ACTION_LIST_REPOS = "com.ibm.sdlc.repos.list";;
    public final static String ACTION_LIST_REPO_COMPLETED = "com.ibm.sdlc.repo.list.completed";
    public final static String ACTION_LIST_REPO_FAILED = "com.ibm.sdlc.repo.list.failed";
    public final static String ACTION_LIST_REPOS_FAILED = "com.ibm.sdlc.repos.list.failed";

    // Comment action types
    public final static String CREATE_COMMENT_ACTION = "com.ibm.sdlc.comment.create";
    public final static String CREATE_COMMENT_ACTION_COMPLETED = "com.ibm.sdlc.comment.create.completed";
    public final static String CREATE_COMMENT_ACTION_FAILED = "com.ibm.sdlc.comment.create.failed";
    
    // Issue action types
    public final static String CREATE_ISSUE_ACTION = "com.ibm.sdlc.issue.create";
    public final static String CREATE_ISSUE_ACTION_COMPLETED = "com.ibm.sdlc.issue.create.completed";
    public final static String CREATE_ISSUE_ACTION_FAILED = "com.ibm.sdlc.issue.create.failed";
    
    // Webhook action types
    public final static String WEBHOOK_ACTION = "com.ibm.sdlc.webhook";
    public final static String WEBHOOK_ACTION_FAILED = "com.ibm.sdlc.webhook.failed";
    public final static String WEBHOOK_CREATE_ACTION_COMPLETED = "com.ibm.sdlc.webhook.create.completed";
    public final static String WEBHOOK_CREATE_ACTION_FAILED = "com.ibm.sdlc.webhook.create.failed";
    public final static String WEBHOOK_DELETE_ACTION_COMPLETED = "com.ibm.sdlc.webhook.delete.complete";
    public final static String WEBHOOK_DELETE_ACTION_FAILED = "com.ibm.sdlc.webhook.delete.failed";


    // Connector specific cloud event types
    static final String INSTANCE_BAD_CREDENTIALS_CE_TYPE = "com.ibm.watson.aiops.connectors.github.badcredentials";
    static final String INSTANCE_UNAUTHORIZED_CE_TYPE = "com.ibm.watson.aiops.connectors.github.unauthorized";
    static final String WEBHOOK_CREATE_FAILURE_CE_TYPE = "com.ibm.watson.aiops.connectors.github.webhook-create-failed";
    static final String WEBHOOK_DELETE_FAILURE_CE_TYPE = "com.ibm.watson.aiops.connectors.github.webhook-delete-failed";
    static final String INSTANCE_ZERO_RESULTS_CE_TYPE = "com.ibm.watson.aiops.connectors.github.zero-results";

    // CE types sent by webhook server
    
    public final static String REPO_DISC = "com.ibm.sdlc.repository.discovered";
    public final static String REPO_DEL = "com.ibm.sdlc.repository.deleted";

    public final static String ISSUE_DISC = "com.ibm.sdlc.issue.discovered";

    public final static String PR_DISC = "com.ibm.sdlc.pr.discovered";

    public final static String COMMIT_DISC = "com.ibm.sdlc.commit.discovered";

    public final static String COMMENT_DISC = "com.ibm.sdlc.comment.discovered";
    public final static String COMMENT_DEL = "com.ibm.sdlc.comment.deleted";

    // Generic CE action failure message
    public final static String CE_ACTION_FAILURE_MESSAGE = "ACTION_FAILED" ;
	
	public static String PUBLIC_GHE_URL = "https://github.com";
	
	// Self identifier
    static final URI SELF_SOURCE = URI.create("template.connectors.aiops.watson.ibm.com/connectorgithub");
    
    // Connector specific cloud event attributes
    static final String ADDRESS_CE_EXT = "address";
    static final String TIMEOUT_SECONDS_CE_EXT = "timeoutseconds";
    static final String RESPONSE_TIME_CE_EXT = "responsetime";
    static final String TOOL_TYPE_CE_EXT = "tooltype";
    static final String TOOL_TYPE_CE_EXT_VALUE = "com.ibm.sdlc.type.scm.github";
    static final String CE_TYPE = "com.ibm.sdlc.repository.discovered";
    static final String CE_EXT_STRUCTURED_CONTENT_MODE = "structuredcontentmode";
    static final String CE_EXT_STRUCTURED_CONTENT_MODE_VALUE = "true";

    // Kafka Topics 
    // TODO: use appropriate topic names. 
    // Action topic is used to produced to when an action is succeful or failed
    static final String ACTION_TOPIC = "cp4waiops-cartridge.test-actions-1";
    // Disc topic is used mainly by webhook to produce CEs for supported github events
    static final String DISC_TOPIC = "cp4waiops-cartridge.test-actions-1";
    // Topics
    static final String ACTION_GITHUB_TOPIC = "cp4waiops-cartridge.connector-github-actions";
    static final String OUTPUT_REPO_TOPIC = "cp4waiops-cartridge.repository";
    static final String OUTPUT_ISSUE_TOPIC = "cp4waiops-cartridge.issue";
    static final String TOPIC_INPUT_LIFECYCLE_EVENTS = "cp4waiops-cartridge.lifecycle.input.events";

    static final String ENCRYPTED_PREFIX = "encrypted:";
    static final String ENV_JWT_CERT_PATH = "grpc-bridge.client-private-key-file";
    // If the environment variable is not set, it defaults to this path.
    // The GitHub connector that is deployed will have the certificates added into the image
    static final String DEFAULT_TOKEN_DECODE_CERT_PATH = "/bindings/grpc-bridge/tls.key";

    // Metrics
    static final String METRICS_LIST_REPO_ACTION = "github.list.repo.action";
    static final String METRICS_LIST_REPO_ACTION_ERROR = "github.list.repo.action.error";

    // Status messages
    static final String STATUS_KEY_CREDENTIALS = "Credential check";
    static final String STATUS_MESSAGE_BAD_CREDENTIALS = "Bad GitHub credentials.";

    static final Duration STATUS_RETRY_DURATION = Duration.ofMinutes(5);

    static final String WEBHOOK_CREATE_ACTION = "create";
    static final String WEBHOOK_DELETE_ACTION = "delete";
}
