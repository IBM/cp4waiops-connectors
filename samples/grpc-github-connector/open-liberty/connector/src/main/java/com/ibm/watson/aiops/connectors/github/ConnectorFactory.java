package com.ibm.watson.aiops.connectors.github;

import com.ibm.cp4waiops.connectors.sdk.Connector;

public class ConnectorFactory implements com.ibm.cp4waiops.connectors.sdk.ConnectorFactory {

    @Override
    public String GetConnectorName() {
    	// For the POC, it has to be template
        return "connector-template";
    }

    @Override
    public String GetComponentName() {
        return "connector";
    }

    @Override
    public Connector Create() {
        return new com.ibm.watson.aiops.connectors.github.Connector();
    }
    
}
