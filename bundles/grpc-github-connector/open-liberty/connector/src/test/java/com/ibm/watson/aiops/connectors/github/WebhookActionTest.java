package com.ibm.watson.aiops.connectors.github;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

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
import org.kohsuke.github.GHHook;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.mockito.Mockito;

import io.cloudevents.CloudEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class WebhookActionTest {

    StatusWriter _mockStatusWriter;
    VaultHelper _mockVaultHelper;
    BlockingQueue<CloudEvent> _eventQueue;
    MeterRegistry _metricRegistry;
    GitHub _github;
    GHRepository _repo;
    GHOrganization _org;
    GHHook _hook;
    ConnectorConfiguration _config;
    ConnectorAction _action;
    Counter _counter;
    Connector _connector;
    List<GHEvent> _events;
    WebhookAction _webhookAction;


    @BeforeEach
    void setup(TestInfo testInfo) throws ConnectorException {
        System.out.println("\n\nRunning test: " + testInfo.getDisplayName() + "\n=====");
        _github = Mockito.mock(GitHub.class);
        _repo = Mockito.mock(GHRepository.class);
        _org = Mockito.mock(GHOrganization.class);
        _hook = Mockito.mock(GHHook.class);
        _config = Mockito.mock(ConnectorConfiguration.class);
        _counter = Mockito.mock(Counter.class);
        _action = Mockito.mock(ConnectorAction.class);
        _events = Arrays.asList(GHEvent.CREATE, GHEvent.DELETE);

        // Counter
        Mockito.doNothing().when(_counter).increment();

        // Connector
        _mockStatusWriter = Mockito.mock(StatusWriter.class);
        _mockVaultHelper = Mockito.mock(VaultHelper.class);
        _eventQueue = new LinkedBlockingQueue<CloudEvent>();
        _metricRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        _connector = new Connector();
        _connector.onInit("_conn_id", "connector", _eventQueue, _mockStatusWriter, _mockVaultHelper);
    }

    @AfterEach
    void teardown() throws InterruptedException {
        System.out.println("\n\nTeardown:\n=====");
    }

    @Test
    @DisplayName("WebhookAction on validation failure target")
    void testWebhookActionOnValidationFailureTarget() throws InterruptedException {

        // Config
        Mockito.when(_config.getTarget()).thenReturn("");

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _webhookAction = new WebhookAction(_action);
        _webhookAction.run();

        Assertions.assertEquals("", _config.getTarget());
        Assertions.assertEquals(1, _eventQueue.size());
        Assertions.assertEquals(ConnectorConstants.WEBHOOK_ACTION_FAILED, _eventQueue.poll().getType());
    }

    @Test
    @DisplayName("WebhookAction on validation failure no webhook action")
    void testWebhookActionOnValidationFailureNoWebhookAction() throws InterruptedException {

        // Config
        Mockito.when(_config.getTarget()).thenReturn("some-target");
        Mockito.when(_config.getWebhookAction()).thenReturn("");

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _webhookAction = new WebhookAction(_action);
        _webhookAction.run();

        Assertions.assertEquals("", _config.getWebhookAction());
        Assertions.assertEquals(1, _eventQueue.size());
        Assertions.assertEquals(ConnectorConstants.WEBHOOK_ACTION_FAILED, _eventQueue.poll().getType());
    }

    @Test
    @DisplayName("WebhookAction on validation failure wrong webhook action")
    void testWebhookActionOnValidationFailureWrongWebhookAction() throws InterruptedException {

        // Config
        Mockito.when(_config.getTarget()).thenReturn("some-target");
        Mockito.when(_config.getWebhookAction()).thenReturn("wrong-webhook-action");

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _webhookAction = new WebhookAction(_action);
        _webhookAction.run();

        Assertions.assertEquals("wrong-webhook-action", _config.getWebhookAction());
        Assertions.assertEquals(1, _eventQueue.size());
        Assertions.assertEquals(ConnectorConstants.WEBHOOK_ACTION_FAILED, _eventQueue.poll().getType());
    }

    @Test
    @DisplayName("WebhookAction on validation failure no events")
    void testWebhookActionOnValidationFailureNoEvent() throws InterruptedException {

        // Config
        Mockito.when(_config.getTarget()).thenReturn("some-target");
        Mockito.when(_config.getWebhookAction()).thenReturn("create");
        Mockito.when(_config.getEvents()).thenReturn(null);

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _webhookAction = new WebhookAction(_action);
        _webhookAction.run();

        Assertions.assertEquals(null, _config.getEvents());
        Assertions.assertEquals(1, _eventQueue.size());
        Assertions.assertEquals(ConnectorConstants.WEBHOOK_ACTION_FAILED, _eventQueue.poll().getType());
    }

    @Test
    @DisplayName("WebhookAction on validation failure no webhook url")
    void testWebhookActionOnValidationFailureNoWebhookUrl() throws InterruptedException {

        // Config
        Mockito.when(_config.getTarget()).thenReturn("some-target");
        Mockito.when(_config.getWebhookAction()).thenReturn("create");
        Mockito.when(_config.getEvents()).thenReturn(_events);
        Mockito.when(_config.getWebhookURL()).thenReturn("");

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _webhookAction = new WebhookAction(_action);
        _webhookAction.run();

        Assertions.assertEquals("", _config.getWebhookURL());
        Assertions.assertEquals(1, _eventQueue.size());
        Assertions.assertEquals(ConnectorConstants.WEBHOOK_ACTION_FAILED, _eventQueue.poll().getType());
    }

    @Test
    @DisplayName("WebhookAction switching live")
    void testWebhookActionSwitchingLive() throws InterruptedException, IOException, ConnectorException {

        Connector conn = new Connector();
        conn.onInit("connectorID", "componentName", _eventQueue, _mockStatusWriter, _mockVaultHelper);
        
        String json = TestUtils
                .getJSONFromTestData("ConnectorConfiguration/ConnectorConfigurationDataNotLive.json");
        CloudEvent cloudEvent = conn.createEvent(System.currentTimeMillis(), ConnectorConstants.WEBHOOK_ACTION,
                json);
        
        // Skip credentials check since we're not making a real webhook
        conn.setCheckCredentialsDuringClientInit(false);
        conn.onConfigure(cloudEvent);

        ConnectorConfiguration connectorConfiguration = conn.getConnectorConfiguration();
        connectorConfiguration.setWebhookURL("http://example.com/webhook");

        // No webhook is created since live is false
        Assertions.assertFalse(connectorConfiguration.getLive());
        Assertions.assertEquals(0, _eventQueue.size());
        // Assertions.assertEquals(ConnectorConstants.WEBHOOK_ACTION_FAILED, _eventQueue.poll().getType());

        json = TestUtils
                .getJSONFromTestData("ConnectorConfiguration/ConnectorConfigurationDataLive.json");
        cloudEvent = conn.createEvent(System.currentTimeMillis(), ConnectorConstants.WEBHOOK_ACTION,
                json);

        conn.onConfigure(cloudEvent);
        connectorConfiguration = conn.getConnectorConfiguration();
        connectorConfiguration.setWebhookURL("http://example.com/webhook");
        Assertions.assertTrue(connectorConfiguration.getLive());
        //Assertions.assertEquals(1, _eventQueue.size());
        //Assertions.assertEquals(ConnectorConstants.WEBHOOK_ACTION_FAILED, _eventQueue.poll().getType());
    }    
    
    /*@Test
    @DisplayName("WebhookAction on create webhook org exception 1")
    void testWebhookActionCreateWebhookOrgExpection1() throws InterruptedException, IOException {

        // Config
        Mockito.when(_config.getTarget()).thenReturn("some-target");
        Mockito.when(_config.getWebhookAction()).thenReturn("create");
        Mockito.when(_config.getEvents()).thenReturn(_events);
        Mockito.when(_config.getWebhookURL()).thenReturn("example.com");
        Mockito.when(_config.getLive()).thenReturn(true);

        // Github 
        Mockito.when(_github.getOrganization("some-target")).thenThrow(new IOException("some-error-msg"));

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _webhookAction = new WebhookAction(_action);
        _webhookAction.run();

        Assertions.assertEquals(1, _eventQueue.size());
        Assertions.assertEquals(ConnectorConstants.WEBHOOK_CREATE_ACTION_FAILED, _eventQueue.poll().getType());
    }*/

    /*@Test
    @DisplayName("WebhookAction on create webhook org exception 2")
    void testWebhookActionCreateWebhookOrgExpection2() throws InterruptedException, IOException {

        // Config
        Mockito.when(_config.getTarget()).thenReturn("some-target");
        Mockito.when(_config.getWebhookAction()).thenReturn("create");
        Mockito.when(_config.getEvents()).thenReturn(_events);
        Mockito.when(_config.getWebhookURL()).thenReturn("http://example.com");
        Mockito.when(_config.getLive()).thenReturn(true);

        Map<String, String> webhookConfig = new HashMap<String, String>();
        webhookConfig.put("url", "http://example.com");
        webhookConfig.put("content_type", "json");
        //webhookConfig.put("secret", getEncodedSecret(webhookSecret));

        // Github 
        Mockito.when(_github.getOrganization("some-target")).thenReturn(_org);
        Mockito.when(_org.createHook(Mockito.anyString(), webhookConfig, Mockito.anyCollection(), true)).thenThrow(new IOException("some-error-msg"));

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _webhookAction = new WebhookAction(_action);
        _webhookAction.run();

        Assertions.assertEquals(1, _eventQueue.size());
        Assertions.assertEquals(ConnectorConstants.WEBHOOK_CREATE_ACTION_FAILED, _eventQueue.poll().getType());
    }*/

    /*@Test
    @DisplayName("WebhookAction on create webhook org exist exception")
    void testWebhookActionCreateWebhookOrgExistExpection() throws InterruptedException, IOException {

        // Config
        Mockito.when(_config.getTarget()).thenReturn("some-target");
        Mockito.when(_config.getWebhookAction()).thenReturn("create");
        Mockito.when(_config.getEvents()).thenReturn(_events);
        Mockito.when(_config.getWebhookURL()).thenReturn("http://example.com");
        Mockito.when(_config.getLive()).thenReturn(true);

        // Github 
        Mockito.when(_github.getOrganization("some-target")).thenReturn(_org);
        Mockito.when(_org.createWebHook(Mockito.any(), Mockito.anyCollection())).thenThrow(new IOException("Hook already exists"));

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _webhookAction = new WebhookAction(_action);
        _webhookAction.run();

        Assertions.assertEquals(0, _eventQueue.size());
    }*/

    /*@Test
    @DisplayName("WebhookAction on create webhook repo exception 1")
    void testWebhookActionCreateWebhookRepoExpection1() throws InterruptedException, IOException {

        // Config
        Mockito.when(_config.getTarget()).thenReturn("some-org/some-repo");
        Mockito.when(_config.getWebhookAction()).thenReturn("create");
        Mockito.when(_config.getEvents()).thenReturn(_events);
        Mockito.when(_config.getWebhookURL()).thenReturn("example.com");
        Mockito.when(_config.getLive()).thenReturn(true);

        // Github 
        Mockito.when(_github.getRepository("some-org/some-repo")).thenThrow(new IOException("some-error-msg"));

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _webhookAction = new WebhookAction(_action);
        _webhookAction.run();

        Assertions.assertEquals(1, _eventQueue.size());
        Assertions.assertEquals(ConnectorConstants.WEBHOOK_CREATE_ACTION_FAILED, _eventQueue.poll().getType());
    }*/

    /*@Test
    @DisplayName("WebhookAction on create webhook repo exception 2")
    void testWebhookActionCreateWebhookRepoExpection2() throws InterruptedException, IOException {

        // Config
        Mockito.when(_config.getTarget()).thenReturn("some-org/some-repo");
        Mockito.when(_config.getWebhookAction()).thenReturn("create");
        Mockito.when(_config.getEvents()).thenReturn(_events);
        Mockito.when(_config.getWebhookURL()).thenReturn("http://example.com");
        Mockito.when(_config.getLive()).thenReturn(true);

        // Github 
        Mockito.when(_github.getRepository("some-org/some-repo")).thenReturn(_repo);
        Mockito.when(_repo.createWebHook(Mockito.any(), Mockito.anyCollection())).thenThrow(new IOException("some-error-msg"));

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _webhookAction = new WebhookAction(_action);
        _webhookAction.run();

        Assertions.assertEquals(1, _eventQueue.size());
        Assertions.assertEquals(ConnectorConstants.WEBHOOK_CREATE_ACTION_FAILED, _eventQueue.poll().getType());
    }*/

    /*@Test
    @DisplayName("WebhookAction on create webhook repo exist exception")
    void testWebhookActionCreateWebhookRepoExistExpection() throws InterruptedException, IOException {

        // Config
        Mockito.when(_config.getTarget()).thenReturn("some-org/some-repo");
        Mockito.when(_config.getWebhookAction()).thenReturn("create");
        Mockito.when(_config.getEvents()).thenReturn(_events);
        Mockito.when(_config.getWebhookURL()).thenReturn("http://example.com");
        Mockito.when(_config.getLive()).thenReturn(true);

        // Github 
        Mockito.when(_github.getRepository("some-org/some-repo")).thenReturn(_repo);
        Mockito.when(_repo.createWebHook(Mockito.any(), Mockito.anyCollection())).thenThrow(new IOException("Hook already exists"));

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _webhookAction = new WebhookAction(_action);
        _webhookAction.run();

        Assertions.assertEquals(0, _eventQueue.size());
    }*/

    /*@Test
    @DisplayName("WebhookAction on create webhook org success")
    void testWebhookActionCreateWebhookOrgSuccess() throws InterruptedException, IOException {

        // Config
        Mockito.when(_config.getTarget()).thenReturn("some-target");
        Mockito.when(_config.getWebhookAction()).thenReturn("create");
        Mockito.when(_config.getEvents()).thenReturn(_events);
        Mockito.when(_config.getWebhookURL()).thenReturn("http://example.com");
        Mockito.when(_config.getLive()).thenReturn(true);
        Mockito.when(_config.getStartTime()).thenReturn((long)10);


        // Github 
        Mockito.when(_github.getOrganization("some-target")).thenReturn(_org);
        Mockito.when(_org.createWebHook(Mockito.any(), Mockito.anyCollection())).thenReturn(_hook);

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _webhookAction = new WebhookAction(_action);
        _webhookAction.run();

        Assertions.assertEquals(1, _eventQueue.size());
        Assertions.assertEquals(ConnectorConstants.WEBHOOK_CREATE_ACTION_COMPLETED, _eventQueue.poll().getType());
    }*/
   
    /*@Test
    @DisplayName("WebhookAction on create webhook repo success")
    void testWebhookActionCreateWebhookRepoSuccess() throws InterruptedException, IOException {
        
        // Config
        Mockito.when(_config.getTarget()).thenReturn("some-org/some-repo");
        Mockito.when(_config.getWebhookAction()).thenReturn("create");
        Mockito.when(_config.getEvents()).thenReturn(_events);
        Mockito.when(_config.getWebhookURL()).thenReturn("http://example.com");
        Mockito.when(_config.getLive()).thenReturn(true);
        Mockito.when(_config.getStartTime()).thenReturn((long)10);

        // Github 
        Mockito.when(_github.getRepository("some-org/some-repo")).thenReturn(_repo);
        Mockito.when(_repo.createWebHook(Mockito.any(), Mockito.anyCollection())).thenReturn(_hook);

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _webhookAction = new WebhookAction(_action);
        _webhookAction.run();

        Assertions.assertEquals(1, _eventQueue.size());
        Assertions.assertEquals(ConnectorConstants.WEBHOOK_CREATE_ACTION_COMPLETED, _eventQueue.poll().getType());
    }*/

    /*@Test
    @DisplayName("WebhookAction on delete webhook repo exception 1")
    void testWebhookActionDeleteWebhookRepoExpection1() throws InterruptedException, IOException {

        // Config
        Mockito.when(_config.getTarget()).thenReturn("some-org/some-repo");
        Mockito.when(_config.getWebhookAction()).thenReturn("delete");
        Mockito.when(_config.getWebhookId()).thenReturn(10);

        // Github 
        Mockito.when(_github.getRepository("some-org/some-repo")).thenThrow(new IOException("some-error-msg"));

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _webhookAction = new WebhookAction(_action);
        _webhookAction.run();

        Assertions.assertEquals(1, _eventQueue.size());
        Assertions.assertEquals(ConnectorConstants.WEBHOOK_DELETE_ACTION_FAILED, _eventQueue.poll().getType());
    }*/

    /*@Test
    @DisplayName("WebhookAction on delete webhook repo exception 2")
    void testWebhookActionDeleteWebhookRepoExpection2() throws InterruptedException, IOException {

        // Config
        Mockito.when(_config.getTarget()).thenReturn("some-org/some-repo");
        Mockito.when(_config.getWebhookAction()).thenReturn("delete");
        Mockito.when(_config.getWebhookId()).thenReturn(10);

        // Github 
        Mockito.when(_github.getRepository("some-org/some-repo")).thenReturn(_repo);
        IOException exception = new IOException("some-error-msg");
        Mockito.doThrow(exception).when(_repo).deleteHook(10);

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _webhookAction = new WebhookAction(_action);
        _webhookAction.run();

        Assertions.assertEquals(1, _eventQueue.size());
        Assertions.assertEquals(ConnectorConstants.WEBHOOK_DELETE_ACTION_FAILED, _eventQueue.poll().getType());
    }*/

    /*@Test
    @DisplayName("WebhookAction on delete webhook org exception 1")
    void testWebhookActionDeleteWebhookOrgExpection1() throws InterruptedException, IOException {

        // Config
        Mockito.when(_config.getTarget()).thenReturn("some-target");
        Mockito.when(_config.getWebhookAction()).thenReturn("delete");
        Mockito.when(_config.getWebhookId()).thenReturn(10);

        // Github 
        Mockito.when(_github.getOrganization("some-target")).thenThrow(new IOException("some-error-msg"));

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _webhookAction = new WebhookAction(_action);
        _webhookAction.run();

        Assertions.assertEquals(1, _eventQueue.size());
        Assertions.assertEquals(ConnectorConstants.WEBHOOK_DELETE_ACTION_FAILED, _eventQueue.poll().getType());
    }*/

    /*@Test
    @DisplayName("WebhookAction on delete webhook org exception 2")
    void testWebhookActionDeleteWebhookOrgExpection2() throws InterruptedException, IOException {

        // Config
        Mockito.when(_config.getTarget()).thenReturn("some-target");
        Mockito.when(_config.getWebhookAction()).thenReturn("delete");
        Mockito.when(_config.getWebhookId()).thenReturn(10);

        // Github 
        Mockito.when(_github.getOrganization("some-target")).thenReturn(_org);
        IOException exception = new IOException("some-error-msg");
        Mockito.doThrow(exception).when(_org).deleteHook(10);

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _webhookAction = new WebhookAction(_action);
        _webhookAction.run();

        Assertions.assertEquals(1, _eventQueue.size());
        Assertions.assertEquals(ConnectorConstants.WEBHOOK_DELETE_ACTION_FAILED, _eventQueue.poll().getType());
    }*/

    /*@Test
    @DisplayName("WebhookAction on delete webhook org success")
    void testWebhookActionDeleteWebhookOrgSuccess() throws InterruptedException, IOException {

        // Config
        Mockito.when(_config.getTarget()).thenReturn("some-target");
        Mockito.when(_config.getWebhookAction()).thenReturn("delete");
        Mockito.when(_config.getWebhookId()).thenReturn(10);
        Mockito.when(_config.getStartTime()).thenReturn((long)10);

        // Github 
        Mockito.when(_github.getOrganization("some-target")).thenReturn(_org);

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _webhookAction = new WebhookAction(_action);
        _webhookAction.run();

        Assertions.assertEquals(1, _eventQueue.size());
        Assertions.assertEquals(ConnectorConstants.WEBHOOK_DELETE_ACTION_COMPLETED, _eventQueue.poll().getType());
    }*/

    /*@Test
    @DisplayName("WebhookAction on delete webhook repo success")
    void testWebhookActionDeleteWebhookRepoSuccess() throws InterruptedException, IOException {

        // Config
        Mockito.when(_config.getTarget()).thenReturn("some-org/some-repo");
        Mockito.when(_config.getWebhookAction()).thenReturn("delete");
        Mockito.when(_config.getWebhookId()).thenReturn(10);
        Mockito.when(_config.getStartTime()).thenReturn((long)10);

        // Github 
        Mockito.when(_github.getRepository("some-org/some-repo")).thenReturn(_repo);

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _webhookAction = new WebhookAction(_action);
        _webhookAction.run();

        Assertions.assertEquals(1, _eventQueue.size());
        Assertions.assertEquals(ConnectorConstants.WEBHOOK_DELETE_ACTION_COMPLETED, _eventQueue.poll().getType());
    }*/
}
