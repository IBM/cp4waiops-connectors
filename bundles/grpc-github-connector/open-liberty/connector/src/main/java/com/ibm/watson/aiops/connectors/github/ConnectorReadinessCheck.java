package com.ibm.watson.aiops.connectors.github;

import javax.enterprise.context.ApplicationScoped;

import com.ibm.cp4waiops.connectors.sdk.SDKCheck;

import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class ConnectorReadinessCheck extends SDKCheck {
    public ConnectorReadinessCheck() {
        super(ConnectorReadinessCheck.class.getName(), Type.READINESS);
    }
}