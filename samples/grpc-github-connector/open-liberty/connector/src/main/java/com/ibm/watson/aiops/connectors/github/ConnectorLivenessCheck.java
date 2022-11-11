package com.ibm.watson.aiops.connectors.github;

import javax.enterprise.context.ApplicationScoped;

import com.ibm.cp4waiops.connectors.sdk.SDKCheck;

import org.eclipse.microprofile.health.Liveness;

@Liveness
@ApplicationScoped
public class ConnectorLivenessCheck extends SDKCheck {
    public ConnectorLivenessCheck() {
    	super(ConnectorLivenessCheck.class.getName(), Type.LIVENESS);
	}
}