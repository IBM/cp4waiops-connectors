package com.ibm.watson.aiops.connectors.github;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import com.ibm.cp4waiops.connectors.sdk.ConnectorException;
import com.ibm.cp4waiops.connectors.sdk.StatusWriter;
import com.ibm.cp4waiops.connectors.sdk.Util;
import com.ibm.cp4waiops.connectors.sdk.VaultHelper;

import org.junit.jupiter.api.Test;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInfo;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHHook;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;
import org.mockito.Mockito;

import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.core.v1.CloudEventV1;
import io.cloudevents.jackson.JsonCloudEventData;
import io.cloudevents.jackson.JsonFormat;
import io.cloudevents.protobuf.ProtobufFormat;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

import com.ibm.watson.aiops.connectors.github.ConnectorConfiguration;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ConnectorTest {

    StatusWriter _mockStatusWriter;
    VaultHelper _mockVaultHelper;
    BlockingQueue<CloudEvent> _eventQueue;
    MeterRegistry _metricRegistry;
    GitHub _github;
    PagedIterable<GHRepository> _repoList;
    PagedIterator<GHRepository> _repos;
    GHRepository _repo;
    GHMyself _myself;
    ConnectorConfiguration _config;
    ConnectorAction _action;
    //Counter _counter;
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
        _eventQueue = new LinkedBlockingQueue<>();
        /*
        _repoList = Mockito.mock(PagedIterable.class);
        _repos = Mockito.mock(PagedIterator.class);
        _repo = Mockito.mock(GHRepository.class);
        _config = Mockito.mock(ConnectorConfiguration.class);
        //_counter = Mockito.mock(Counter.class);
        _action = Mockito.mock(ConnectorAction.class);
        _repoListAction = Mockito.mock(RepoListAction.class);*/

        // Counter
        //Mockito.doNothing().when(_counter).increment();

        // Connector
        _mockStatusWriter = Mockito.mock(StatusWriter.class);
        _mockVaultHelper = Mockito.mock(VaultHelper.class);
        
        /*
        _eventQueue = new LinkedList<>();
        _metricRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        _connector = new Connector();
        _connector.onInit("_conn_id", "connector", _eventQueue, _mockStatusWriter, _mockVaultHelper);*/
    }

    @AfterEach
    void teardown() throws InterruptedException {
        System.out.println("\n\nTeardown:\n=====");
    }


    @Test
    @DisplayName("Testing register metrics")
    void testRegisteMetrics() throws InterruptedException, IOException, ConnectorException, URISyntaxException {
        Connector connector = new Connector();
        PrometheusMeterRegistry metrics = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        metrics.config().commonTags("ConnectorID", "_connectorID", "ComponentName","ComponentName");

        // Github 
        Mockito.when(_github.getMyself()).thenThrow(new IOException("some-error-msg"));

        String jsonBody = TestUtils.getJSONFromTestData("ConnectorConfiguration/ConfigBody.json");

        CloudEvent ce = TestUtils.buildCloudEventWithActionBody(ConnectorConstants.ACTION_LIST_REPOS, jsonBody);

        connector.onInit("connectorID", "componentName", _eventQueue, _mockStatusWriter, _mockVaultHelper);
        connector.registerMetrics(metrics);
        connector.setCheckCredentialsDuringClientInit(false);

        Assertions.assertEquals(0, connector.getListReposActionCounter().count());
        Assertions.assertEquals(0, connector.getListReposActionErrorCounter().count());
        connector.onConfigure(ce);

        System.out.println("ACTION QUEUE SIZE BEFORE: " + connector.actionQueue.size());
        ConnectorAction repoAction = connector.actionQueue.poll();
        // Get rid of webhook action
        connector.actionQueue.poll();
        repoAction.setGithub(_github);
        connector.actionQueue.add(repoAction);

        connector.processActionFromActionQueue();
        TestUtils.waitForSimpleRun();
        System.out.println("ACTION QUEUE SIZE BEFORE: " + connector.actionQueue.size());

        Assertions.assertEquals(1, connector.getListReposActionCounter().count());
        Assertions.assertEquals(1, connector.getListReposActionErrorCounter().count());
    }


    @Test
    @DisplayName("Bad credentials test")
    void testBadCredentials() throws InterruptedException, IOException, ConnectorException, URISyntaxException {
        Connector connector = new Connector();
        PrometheusMeterRegistry metrics = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        metrics.config().commonTags("ConnectorID", "_connectorID", "ComponentName","ComponentName");

        String jsonBody = TestUtils.getJSONFromTestData("ConnectorConfiguration/ConfigBodyPublicBadCredentials.json");

        CloudEvent ce = TestUtils.buildCloudEventWithActionBody(ConnectorConstants.ACTION_LIST_REPOS, jsonBody);

        connector.setSleepInterval(0);
        connector.onInit("connectorID", "componentName", _eventQueue, _mockStatusWriter, _mockVaultHelper);

        String foundError = "";
        try {
            connector.onConfigure(ce);
        } catch(ConnectorException exception){
            foundError = exception.getMessage();
        }

        Assertions.assertEquals(ConnectorConstants.STATUS_MESSAGE_BAD_CREDENTIALS, foundError);
    }
}