package com.ibm.watson.aiops.connectors.github;

import java.io.IOException;
import java.util.LinkedList;

import com.ibm.cp4waiops.connectors.sdk.ConnectorException;
import com.ibm.cp4waiops.connectors.sdk.StatusWriter;
import com.ibm.cp4waiops.connectors.sdk.VaultHelper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInfo;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.mockito.Mockito;

import io.cloudevents.CloudEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

import java.util.concurrent.BlockingQueue;

public class TokenTest {

    StatusWriter _mockStatusWriter;
    VaultHelper _mockVaultHelper;
    LinkedList<CloudEvent> _eventQueue;
    MeterRegistry _metricRegistry;
    GitHub _github;
    GHRepository _repo;
    GHIssueBuilder _builder;
    ConnectorConfiguration _config;
    ConnectorAction _action;
    Counter _counter;
    Connector _connector;
    IssueCreateAction _issueCreateAction;
    
    @Test
    @DisplayName("Test verify plaintext token loading")
    void testVerifyPlaintextToken() throws InterruptedException, IOException {
        ConnectorConfiguration configuration = new ConnectorConfiguration();
        
        String json = TestUtils.getJSONFromTestData("ConnectorConfiguration/BasicPlaintextToken.json");
        configuration.loadDataFromJson(json);

        Assertions.assertEquals("notarealtoken", configuration.getToken());
    }

    @Test
    @DisplayName("Test verify encrypted token loading")
    void testVerifyEncryptedToken() throws InterruptedException, IOException {
        ConnectorConfiguration configuration = new ConnectorConfiguration();
        
        String json = TestUtils.getJSONFromTestData("ConnectorConfiguration/BasicEncryptedToken.json");

        configuration.setTokenDecodeCertLocation("src/test/data/ConnectorConfiguration/JWTToken.txt");
        configuration.loadDataFromJson(json);

        // To avoid having this flagged as a PAT being used, only check a few characters
        String token = configuration.getToken();
        Assertions.assertTrue(token.startsWith("ov2IP"));
        Assertions.assertTrue(token.endsWith("ayRc7"));
    }
}
