package com.ibm.watson.aiops.connectors.github;

import java.net.MalformedURLException;
import java.net.URL;

public class CommonActionUtilities {
	// Given a GitHub hostname, provide the endpoint URL
	
	
	// TODO: provide a way to overide the /api/v3 endpoint
	public static String getEndPointURL(String hostName) throws MalformedURLException {
		if (hostName != null) {
			URL gitHubPublicURL = new URL(ConnectorConstants.PUBLIC_GHE_URL);
			
			if (hostName.strip().endsWith("/")) {
				hostName = hostName.substring(0, hostName.length() - 1);
			}
			
			if (gitHubPublicURL.equals(new URL(hostName))) {
				// Public GHE
				return "https://api.github.com";
			}
			else {
				System.out.println("GETENDPOINT " + hostName + "/api/v3");
				return hostName + "/api/v3";
			}
		}
		return null;
	}
}
