package com.ibm.watson.aiops.connectors.github;

import javax.enterprise.context.ApplicationScoped;

import com.ibm.cp4waiops.connectors.sdk.ConnectorManager;
import com.ibm.cp4waiops.connectors.sdk.StandardConnectorManager;

@ApplicationScoped
public class ManagerInstance {
    private ConnectorManager manager = new StandardConnectorManager(new ConnectorFactory());

    public ConnectorManager getConnectorManager() {
        return manager;
    }
}
