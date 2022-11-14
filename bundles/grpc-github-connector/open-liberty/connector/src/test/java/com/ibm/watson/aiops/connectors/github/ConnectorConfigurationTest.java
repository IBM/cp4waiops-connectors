package com.ibm.watson.aiops.connectors.github;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import com.ibm.cp4waiops.connectors.sdk.Connector;
import com.ibm.cp4waiops.connectors.sdk.ConnectorException;
import com.ibm.cp4waiops.connectors.sdk.StatusWriter;
import com.ibm.cp4waiops.connectors.sdk.VaultHelper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInfo;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;
import org.mockito.Mockito;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

public class ConnectorConfigurationTest {

    StatusWriter _mockStatusWriter;
    VaultHelper _mockVaultHelper;
    MeterRegistry _metricRegistry;
    GitHub _github;
    PagedIterable<GHRepository> _repoList;
    PagedIterator<GHRepository> _repos;
    GHRepository _repo;
    GHMyself _myself;
    ConnectorConfiguration _config;
    ConnectorAction _action;
    Connector _connector;
    List<GHEvent> _events;
    RepoListAction _repoListAction;

    // For querying GitHub, use public GitHub and login anonymously.
    // Trying to mock myself.listRepositories(), which returns 
    // PagedIterable<GHRepository> was hard to do.
    // 
    // One way to get PagedIterable<GHRepository> from a given repository
    // is to get the forks. I created a fork with another GitHub account
    // and sort by the oldest fork to guarantee I can always get the repository
    // of interest.
    static final String GITHUB_PUBLIC_ENDPOINT="https://github.com";
    static final String GITHUB_PUBLIC_API_ENDPOINT="https://api.github.com";


    @BeforeEach
    void setup(TestInfo testInfo) throws ConnectorException, IOException {
        System.out.println("\n\nRunning test: " + testInfo.getDisplayName() + "\n=====");
        _github = Mockito.mock(GitHub.class);
        _myself = Mockito.mock(GHMyself.class);
    }

    @AfterEach
    void teardown() throws InterruptedException {
        System.out.println("\n\nTeardown:\n=====");
    }

    @Test
    @DisplayName("Testing configuration get and set methods")
    void testConfigurationSetAndGetMethods() throws InterruptedException, IOException, ConnectorException, URISyntaxException {
        PrometheusMeterRegistry metrics = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        metrics.config().commonTags("ConnectorID", "_connectorID", "ComponentName","ComponentName");

        // Github 
        Mockito.when(_github.getMyself()).thenThrow(new IOException("some-error-msg"));

        String jsonBody = TestUtils.getJSONFromTestData("ConnectorConfiguration/ConnectorConfigurationAllProperties.json");

        ConnectorConfiguration connectionCreateCfg = new ConnectorConfiguration();
    	connectionCreateCfg.loadDataFromJson(jsonBody);

        Assertions.assertEquals(true,connectionCreateCfg.getLive());
        Assertions.assertEquals("IBM/cp4waiops-connectors-java-template",connectionCreateCfg.getTarget());
        Assertions.assertEquals("https://github.com",connectionCreateCfg.getURL());
        List<String> patterns = connectionCreateCfg.getPatterns();
        Assertions.assertEquals(1,patterns.size());
        Assertions.assertEquals("^IBM/cp4waiops-connectors-java-template.*",patterns.get(0));


        Assertions.assertEquals("github-secret",connectionCreateCfg.getVaultRef());
        Assertions.assertEquals("CustomSecret/CustomPath",connectionCreateCfg.getVaultReferencePath());
        Assertions.assertEquals("CustomKey",connectionCreateCfg.getVaultReferenceKey());

        // Test setter methods
        connectionCreateCfg.setLive(true);
        Assertions.assertEquals(true,connectionCreateCfg.getLive());

        connectionCreateCfg.setTarget("NewTarget");
        Assertions.assertEquals("NewTarget",connectionCreateCfg.getTarget());

        connectionCreateCfg.setURL("https://github.example.com");
        Assertions.assertEquals("https://github.example.com",connectionCreateCfg.getURL());

        List<String> patterns2 = new ArrayList<String>();
        patterns2.add("one");
        patterns2.add("two");
        connectionCreateCfg.setPatterns(patterns2);
        patterns = connectionCreateCfg.getPatterns();
        Assertions.assertEquals(2,patterns.size());
        Assertions.assertEquals("one",patterns.get(0));
        Assertions.assertEquals("two",patterns.get(1));

        connectionCreateCfg.setVaultRef("NewVaultRef");
        Assertions.assertEquals("NewVaultRef",connectionCreateCfg.getVaultRef());

        connectionCreateCfg.setVaultReferencePath("NewRefPath");
        Assertions.assertEquals("NewRefPath",connectionCreateCfg.getVaultReferencePath());

        connectionCreateCfg.setVaultReferenceKey("NewRefKey");
        Assertions.assertEquals("NewRefKey",connectionCreateCfg.getVaultReferenceKey());
    }

    @Test
    @DisplayName("Testing live false for no webhook")
    void testNoWebhook() throws InterruptedException, IOException, ConnectorException, URISyntaxException {
        PrometheusMeterRegistry metrics = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        metrics.config().commonTags("ConnectorID", "_connectorID", "ComponentName","ComponentName");

        // Github 
        Mockito.when(_github.getMyself()).thenThrow(new IOException("some-error-msg"));

        String jsonBody = TestUtils.getJSONFromTestData("ConnectorConfiguration/ConnectorConfigurationNoWebhook.json");

        ConnectorConfiguration connectionCreateCfg = new ConnectorConfiguration();
    	connectionCreateCfg.loadDataFromJson(jsonBody);

        Assertions.assertEquals(false,connectionCreateCfg.getLive());
    }    

    @Test
    @DisplayName("Testing configuration changed methods")
    void testConfigurationChanged() throws InterruptedException, IOException, ConnectorException, URISyntaxException {
        ConnectorConfiguration oldConfig = new ConnectorConfiguration();
        ConnectorConfiguration newConfig = new ConnectorConfiguration();
        com.ibm.watson.aiops.connectors.github.Connector c = new com.ibm.watson.aiops.connectors.github.Connector();
        Assertions.assertFalse(c.hasCoreConfigChanged(oldConfig, newConfig));

        // URL has changed
        oldConfig.setURL("https://github.com");
        newConfig.setURL("https://github.example.com");
        Assertions.assertTrue(c.hasCoreConfigChanged(oldConfig, newConfig));
        // Swap new and old config to ensure logic works both ways
        Assertions.assertTrue(c.hasCoreConfigChanged(newConfig, oldConfig));

        // Set to same URL
        oldConfig.setURL("https://github.example.com");
        Assertions.assertFalse(c.hasCoreConfigChanged(oldConfig, newConfig));
        Assertions.assertFalse(c.hasCoreConfigChanged(newConfig, oldConfig));

        // Token has changed
        oldConfig.setToken("FAKETOKENOLD");
        newConfig.setToken("FAKETOKENNEW");
        Assertions.assertTrue(c.hasCoreConfigChanged(oldConfig, newConfig));
        Assertions.assertTrue(c.hasCoreConfigChanged(newConfig, oldConfig));

        // Set to same token
        oldConfig.setToken("FAKETOKEN");
        newConfig.setToken("FAKETOKEN");
        Assertions.assertFalse(c.hasCoreConfigChanged(oldConfig, newConfig));
        Assertions.assertFalse(c.hasCoreConfigChanged(newConfig, oldConfig));

        // Vault reference has changed
        oldConfig.setVaultRef("VAULTREFOLD");
        newConfig.setVaultRef("VAULTREFNEW");
        Assertions.assertTrue(c.hasCoreConfigChanged(oldConfig, newConfig));
        Assertions.assertTrue(c.hasCoreConfigChanged(newConfig, oldConfig));

        // Set same vault reference
        oldConfig.setVaultRef("VAULTREF");
        newConfig.setVaultRef("VAULTREF");
        Assertions.assertFalse(c.hasCoreConfigChanged(oldConfig, newConfig));
        Assertions.assertFalse(c.hasCoreConfigChanged(newConfig, oldConfig));

        // Vault reference key has changed
        oldConfig.setVaultReferenceKey("VAULTREFKEYOLD");
        newConfig.setVaultReferenceKey("VAULTREFKEYNEW");
        Assertions.assertTrue(c.hasCoreConfigChanged(oldConfig, newConfig));
        Assertions.assertTrue(c.hasCoreConfigChanged(newConfig, oldConfig));
        
        // Set same vault reference key
        oldConfig.setVaultReferenceKey("VAULTREFKEY");
        newConfig.setVaultReferenceKey("VAULTREFKEY");
        Assertions.assertFalse(c.hasCoreConfigChanged(oldConfig, newConfig));
        Assertions.assertFalse(c.hasCoreConfigChanged(newConfig, oldConfig)); 

        // Vault reference path has changed
        oldConfig.setVaultReferencePath("VAULTREF/PATH/OLD");
        newConfig.setVaultReferencePath("VAULTREF/PATH/NEW");
        Assertions.assertTrue(c.hasCoreConfigChanged(oldConfig, newConfig));
        Assertions.assertTrue(c.hasCoreConfigChanged(newConfig, oldConfig));
        
        // Set same vault reference path
        oldConfig.setVaultReferencePath("VAULTREF/PATH");
        newConfig.setVaultReferencePath("VAULTREF/PATH");
        Assertions.assertFalse(c.hasCoreConfigChanged(oldConfig, newConfig));
        Assertions.assertFalse(c.hasCoreConfigChanged(newConfig, oldConfig));

        // Set target
        oldConfig.setTarget("TARGETOLD");
        newConfig.setTarget("TARGETNEW");
        // We don't expect a core change, since target is specific for webhooks
        Assertions.assertFalse(c.hasCoreConfigChanged(oldConfig, newConfig));
        Assertions.assertFalse(c.hasCoreConfigChanged(newConfig, oldConfig));

        Assertions.assertTrue(c.hasWebHookConfigChanged(oldConfig, newConfig));
        Assertions.assertTrue(c.hasWebHookConfigChanged(newConfig, oldConfig));

        oldConfig.setTarget("TARGET");
        newConfig.setTarget("TARGET");
        Assertions.assertFalse(c.hasWebHookConfigChanged(oldConfig, newConfig));
        Assertions.assertFalse(c.hasWebHookConfigChanged(newConfig, oldConfig));

        // Set live
        oldConfig.setTarget("LIVEOLD");
        newConfig.setTarget("LIVENEW");
        // We don't expect a core change, since target is specific for webhooks
        Assertions.assertFalse(c.hasCoreConfigChanged(oldConfig, newConfig));
        Assertions.assertFalse(c.hasCoreConfigChanged(newConfig, oldConfig));

        Assertions.assertTrue(c.hasWebHookConfigChanged(oldConfig, newConfig));
        Assertions.assertTrue(c.hasWebHookConfigChanged(newConfig, oldConfig));

        oldConfig.setTarget("LIVE");
        newConfig.setTarget("LIVE");
        Assertions.assertFalse(c.hasWebHookConfigChanged(oldConfig, newConfig));
        Assertions.assertFalse(c.hasWebHookConfigChanged(newConfig, oldConfig));

        // Check patterns
        List<String> listOld = new ArrayList<String>();
        listOld.add("^IBM/cp4waiops-connectors-java-template.*");

        List<String> listNew = new ArrayList<String>();
        listNew.add("^IBM/cp4waiops-connectors-java-template.*");

        oldConfig.setPatterns(listOld);
        newConfig.setPatterns(listNew);
        
        Assertions.assertFalse(c.hasRepoListConfigChanged(oldConfig, newConfig));
        Assertions.assertFalse(c.hasRepoListConfigChanged(newConfig, oldConfig));

        listNew.add("AnotherPath");
        Assertions.assertTrue(c.hasRepoListConfigChanged(oldConfig, newConfig));
        Assertions.assertTrue(c.hasRepoListConfigChanged(newConfig, oldConfig));

        listOld.add("AnotherPath");
        Assertions.assertFalse(c.hasRepoListConfigChanged(oldConfig, newConfig));
        Assertions.assertFalse(c.hasRepoListConfigChanged(newConfig, oldConfig));

        listOld.add("AnotherPathOld");
        listNew.add("AnotherPathNew");
        Assertions.assertTrue(c.hasRepoListConfigChanged(oldConfig, newConfig));
        Assertions.assertTrue(c.hasRepoListConfigChanged(newConfig, oldConfig));

        oldConfig.setPatterns(null);
        Assertions.assertTrue(c.hasRepoListConfigChanged(oldConfig, newConfig));
        Assertions.assertTrue(c.hasRepoListConfigChanged(newConfig, oldConfig));
    }
}