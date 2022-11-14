package com.ibm.watson.aiops.connectors.github;

public class ConnectorActionFactory {
	public static Runnable getRunnableAction(ConnectorAction action) {
		if (action != null) {
			if (ConnectorConstants.ACTION_LIST_REPOS.equals(action.getActionType())){
				return new RepoListAction(action);
			}
			else if (ConnectorConstants.CREATE_ISSUE_ACTION.equals(action.getActionType())) {
				return new IssueCreateAction(action);
			}
			else if (ConnectorConstants.CREATE_COMMENT_ACTION.equals(action.getActionType())) {
				return new CommentCreateAction(action);
			}
			else if (ConnectorConstants.WEBHOOK_ACTION.equals(action.getActionType())) {
				return new WebhookAction(action);
			}
		}
		return null;
	}
}
