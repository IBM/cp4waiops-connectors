package com.ibm.watson.aiops.connectors.github;

import java.io.IOException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;
import org.json.JSONException;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterable;

import io.cloudevents.CloudEvent;
import io.micrometer.core.instrument.Counter;

import org.kohsuke.github.GHRepository.Visibility;

public class RepoListAction implements Runnable {
	
	ConnectorAction action;
	
	static final Logger logger = Logger.getLogger(RepoListAction.class.getName());
	
	protected static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	public RepoListAction(ConnectorAction action) {
		this.action = action;
	}
	
	protected String getTraceTimestamp() {
		Timestamp timestamp = new Timestamp(System.currentTimeMillis());
		
		return simpleDateFormat.format(timestamp); 
	}
	
	@Override
	public void run() {
		logger.log(Level.INFO,"Run RepoListAction");

		Counter actionCounter = action.getActionCounter();
        Counter actionErrorCounter = action.getActionErrorCounter();

		actionCounter.increment();

		// Need to error check this...
		ConnectorConfiguration config = action.getConfiguration();
    	
		GitHub github = action.getGitHub();

		Connector connector = action.getConnector();
					
		CloudEvent ce;
    	
    	try {
			
			GHMyself myself = github.getMyself();
	    	
			logger.log(Level.INFO,"List all repos begins at " + getTraceTimestamp());
			// listRepositories returns instantly (non-blocking), reading through the pages is the time 
			// consuming part, so the ending time is after all the repos are processed
			PagedIterable<GHRepository> repoList = myself.listRepositories();
	    	
	    	String jsonString = "";
	    	
	    	
	    	// PATTERNS
	    	List<String> patterns = config.getPatterns();
			int numRepos = 0;
	    	
			for (GHRepository repo : repoList) {				
				ce = getRepoListCloudEvent(config, repo, connector, patterns);
				if (ce != null)
					connector.emitCloudEvent(ConnectorConstants.OUTPUT_REPO_TOPIC, null, ce);
					numRepos++;
	    	}

			logger.log(Level.INFO,"Num repos found: " + numRepos);
			if (numRepos == 0){
				connector.triggerListRepoAlert(config);
			}
	    	
		} catch (IOException e) {
			ce = connector.createEvent((System.nanoTime() - config.getStartTime()), ConnectorConstants.ACTION_LIST_REPOS_FAILED, ConnectorConstants.CE_ACTION_FAILURE_MESSAGE);
			connector.emitCloudEvent(ConnectorConstants.ACTION_GITHUB_TOPIC, null, ce);
			actionErrorCounter.increment();
			connector.triggerCommonAlerts(config, e);
		}
	}
	
	protected boolean testMultipleRegex(String name, List<String> patterns) {
		if (patterns != null && !patterns.isEmpty()) {
			for (String regex : patterns) {
				Pattern p = Pattern.compile(regex);
				Matcher m = p.matcher(name);
				
				if(m.matches()) {
					// One pattern was found to match
					return true;
				}
			}
		}
		
		return false;
	}

    private static String genName(String uri) {
        String host = uri.substring(uri.indexOf("://") + 3);
        return host.toLowerCase().replaceAll("[^a-z0-9-]", "-");
    }  
    
    protected String getParent(String fullName) {
    	if(fullName != null) {
    		String[] parts = fullName.split("/");
    		if (parts.length > 0) {
    			return parts[0];
    		}
    	}
    	return null;
    }    

	protected CloudEvent getRepoListCloudEvent(ConnectorConfiguration config, GHRepository repo, Connector connector, List<String> patterns) throws JSONException, IOException{
		CloudEvent ce = null;
		String jsonString = "";
		
		String fullName = repo.getFullName();

		//logger.log(Level.INFO,"getRepoListCloudEvent with fullName=" + fullName + " and regex=" + patterns.toString());
				
		if(testMultipleRegex(fullName, patterns)) {
			// Parent in AI OPs 3.2 is not the same as the parent from the GitHub API.
			// It is the org or owner part of the full repo name.
			
			DateTimeFormatter isoFormat = DateTimeFormatter.ISO_INSTANT;
			
			jsonString = new JSONObject()
				.put("uri", config.getURL())
				.put("type", ConnectorConstants.CE_TYPE)
				.put("parent", getParent(repo.getFullName()))
				.put("repo_name", repo.getName())
				.put("repo_id", repo.getId() + "-" + genName(config.getURL()))
				.put("description", repo.getDescription())
				.put("private", Boolean.toString(repo.getVisibility() == Visibility.PRIVATE))
				.put("fork", Boolean.toString(repo.isFork()))
				.put("default_branch", repo.getDefaultBranch())
				.put("created_at", isoFormat.format(repo.getCreatedAt().toInstant()))
				.put("updated_at", isoFormat.format(repo.getUpdatedAt().toInstant()))
				.put("pushed_at", isoFormat.format(repo.getPushedAt().toInstant()))
				.put("languages", repo.getLanguage())
				.toString();
			
			ce = connector.createEvent((System.nanoTime() - config.getStartTime()), ConnectorConstants.CE_TYPE, jsonString);
		}
		return ce;
	}
}
