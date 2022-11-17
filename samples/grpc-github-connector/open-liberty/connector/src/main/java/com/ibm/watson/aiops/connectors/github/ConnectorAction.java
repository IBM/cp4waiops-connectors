package com.ibm.watson.aiops.connectors.github;

import org.kohsuke.github.GitHub;

import io.micrometer.core.instrument.Counter;

public class ConnectorAction {
	String actionType;
	ConnectorConfiguration configuration;
	Connector connector;
	GitHub github;
	Counter actionCounter;
	Counter actionErrorCounter;
	 
	public ConnectorAction(String actionType, ConnectorConfiguration configuration, GitHub github, Connector connector, Counter actionCounter, Counter actionErrorCounter) {
		this.actionType = actionType;
		this.configuration = configuration;
		this.github = github;
		this.connector = connector;
		this.actionCounter = actionCounter;
		this.actionErrorCounter = actionErrorCounter;
	}
	
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("Action: " + actionType + " with configuration: " + configuration.toString());		
		
		return sb.toString();
	}
	
	public String getActionType() {
		return actionType;
	}
	
	public ConnectorConfiguration getConfiguration() {
		return configuration;
	}

	public GitHub getGitHub() {
		return github;
	}
	
	public  Connector getConnector() {
		return connector;
	}

	public Counter getActionCounter() {
		return actionCounter;
	}

	public Counter getActionErrorCounter() {
		return actionErrorCounter;
	}

	public void setGithub(GitHub github){
		this.github = github;
	}
}
