package com.ibm.watson.aiops.connectors.github;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
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
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;
import org.mockito.Mockito;

import io.cloudevents.CloudEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class RepoListActionTest {

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
    Counter _counter;
    Connector _connector;
    List<GHEvent> _events;
    RepoListAction _repoListAction;


    @BeforeEach
    void setup(TestInfo testInfo) throws ConnectorException {
        System.out.println("\n\nRunning test: " + testInfo.getDisplayName() + "\n=====");
        _github = Mockito.mock(GitHub.class);
        _myself = Mockito.mock(GHMyself.class);
        _repoList = Mockito.mock(PagedIterable.class);
        _repos = Mockito.mock(PagedIterator.class);
        _repo = Mockito.mock(GHRepository.class);
        _config = Mockito.mock(ConnectorConfiguration.class);
        _counter = Mockito.mock(Counter.class);
        _action = Mockito.mock(ConnectorAction.class);

        // Counter
        Mockito.doNothing().when(_counter).increment();

        // Connector
        _mockStatusWriter = Mockito.mock(StatusWriter.class);
        _mockVaultHelper = Mockito.mock(VaultHelper.class);
        _eventQueue = new LinkedBlockingQueue<>();
        _metricRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        _connector = new Connector();
        _connector.onInit("_conn_id", "connector", _eventQueue, _mockStatusWriter, _mockVaultHelper);
    }

    @AfterEach
    void teardown() throws InterruptedException {
        System.out.println("\n\nTeardown:\n=====");
    }

    @Test
    @DisplayName("RepoListAction on exception")
    void testRepoListActionOnException() throws InterruptedException, IOException {
        // Github 
        Mockito.when(_github.getMyself()).thenThrow(new IOException("some-error-msg"));

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _repoListAction = new RepoListAction(_action);
        _repoListAction.run();

        Assertions.assertEquals(1, _eventQueue.size());
        Assertions.assertEquals(ConnectorConstants.ACTION_LIST_REPOS_FAILED, _eventQueue.poll().getType());
    }
}
