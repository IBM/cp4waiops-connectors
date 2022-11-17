package com.ibm.watson.aiops.connectors.github;

import java.io.IOException;

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
import org.kohsuke.github.GHPullRequest;
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

public class CommentCreateActionTest {

    StatusWriter _mockStatusWriter;
    VaultHelper _mockVaultHelper;
    BlockingQueue<CloudEvent> _eventQueue;
    MeterRegistry _metricRegistry;
    GitHub _github;
    GHRepository _repo;
    GHIssue _issue;
    GHPullRequest _pr;
    ConnectorConfiguration _config;
    ConnectorAction _action;
    Counter _counter;
    Connector _connector;
    CommentCreateAction _commentCreateAction;


    @BeforeEach
    void setup(TestInfo testInfo) throws ConnectorException {
        System.out.println("\n\nRunning test: " + testInfo.getDisplayName() + "\n=====");
        _github = Mockito.mock(GitHub.class);
        _repo = Mockito.mock(GHRepository.class);
        _issue = Mockito.mock(GHIssue.class);
        _pr =  Mockito.mock(GHPullRequest.class);
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
    @DisplayName("CommentCreateAction on validation failure parent")
    void testCommentCreateActionOnValidationFailureParent() throws InterruptedException {

        // Config
        Mockito.when(_config.getParent()).thenReturn("");

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _commentCreateAction = new CommentCreateAction(_action);
        _commentCreateAction.run();

        Assertions.assertEquals("", _config.getParent());
        Assertions.assertEquals(1, _eventQueue.size());
        Assertions.assertEquals(ConnectorConstants.CREATE_COMMENT_ACTION_FAILED, _eventQueue.poll().getType());
    }

    @Test
    @DisplayName("CommentCreateAction on validation failure repo")
    void testCommentCreateActionOnValidationFailureRepo() throws InterruptedException {

        // Config
        Mockito.when(_config.getParent()).thenReturn("some-parent");
        Mockito.when(_config.getRepoName()).thenReturn("");

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _commentCreateAction = new CommentCreateAction(_action);
        _commentCreateAction.run();

        Assertions.assertEquals("", _config.getRepoName());
        Assertions.assertEquals(1, _eventQueue.size());
        Assertions.assertEquals(ConnectorConstants.CREATE_COMMENT_ACTION_FAILED, _eventQueue.poll().getType());
    }

    @Test
    @DisplayName("CommentCreateAction on validation failure item_id")
    void testCommentCreateActionOnValidationFailureItemId() throws InterruptedException {

        // Config
        Mockito.when(_config.getParent()).thenReturn("some-parent");
        Mockito.when(_config.getRepoName()).thenReturn("some-repo");
        Mockito.when(_config.getItemId()).thenReturn(ConnectorConfiguration.DEFAULT_ID);

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _commentCreateAction = new CommentCreateAction(_action);
        _commentCreateAction.run();

        Assertions.assertEquals(ConnectorConfiguration.DEFAULT_ID, _config.getItemId());
        Assertions.assertEquals(1, _eventQueue.size());
        Assertions.assertEquals(ConnectorConstants.CREATE_COMMENT_ACTION_FAILED, _eventQueue.poll().getType());
    }

    @Test
    @DisplayName("CommentCreateAction on validation failure no issue_type")
    void testCommentCreateActionOnValidationFailureNoIssueType() throws InterruptedException {

        // Config
        Mockito.when(_config.getParent()).thenReturn("some-parent");
        Mockito.when(_config.getRepoName()).thenReturn("some-repo");
        Mockito.when(_config.getItemId()).thenReturn(10);
        Mockito.when(_config.getIssueType()).thenReturn("");

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _commentCreateAction = new CommentCreateAction(_action);
        _commentCreateAction.run();

        Assertions.assertEquals("", _config.getIssueType());
        Assertions.assertEquals(1, _eventQueue.size());
        Assertions.assertEquals(ConnectorConstants.CREATE_COMMENT_ACTION_FAILED, _eventQueue.poll().getType());
    }

    @Test
    @DisplayName("CommentCreateAction on validation failure wrong issue_type")
    void testCommentCreateActionOnValidationFailureWrongIssueType() throws InterruptedException {

        // Config
        Mockito.when(_config.getParent()).thenReturn("some-parent");
        Mockito.when(_config.getRepoName()).thenReturn("some-repo");
        Mockito.when(_config.getItemId()).thenReturn(10);
        Mockito.when(_config.getIssueType()).thenReturn("wrong-issue-type");

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _commentCreateAction = new CommentCreateAction(_action);
        _commentCreateAction.run();

        Assertions.assertEquals("wrong-issue-type", _config.getIssueType());
        Assertions.assertEquals(1, _eventQueue.size());
        Assertions.assertEquals(ConnectorConstants.CREATE_COMMENT_ACTION_FAILED, _eventQueue.poll().getType());
    }

    @Test
    @DisplayName("CommentCreateAction on validation failure body")
    void testCommentCreateActionOnValidationFailureBody() throws InterruptedException {

        // Config
        Mockito.when(_config.getParent()).thenReturn("some-parent");
        Mockito.when(_config.getRepoName()).thenReturn("some-repo");
        Mockito.when(_config.getItemId()).thenReturn(10);
        Mockito.when(_config.getIssueType()).thenReturn("pullRequest");
        Mockito.when(_config.getBody()).thenReturn("");

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _commentCreateAction = new CommentCreateAction(_action);
        _commentCreateAction.run();

        Assertions.assertEquals("", _config.getBody());
        Assertions.assertEquals(1, _eventQueue.size());
        Assertions.assertEquals(ConnectorConstants.CREATE_COMMENT_ACTION_FAILED, _eventQueue.poll().getType());
    }

    @Test
    @DisplayName("CommentCreateAction on exception 1")
    void testCommentCreateActionOnException1() throws IOException, InterruptedException {

        // Config
        Mockito.when(_config.getParent()).thenReturn("some-parent");
        Mockito.when(_config.getRepoName()).thenReturn("some-repo");
        Mockito.when(_config.getItemId()).thenReturn(10);
        Mockito.when(_config.getIssueType()).thenReturn("pullRequest");
        Mockito.when(_config.getBody()).thenReturn("some-body");
        Mockito.when(_config.getStartTime()).thenReturn((long)10);
        
        // Github 
        Mockito.when(_github.getRepository("some-parent/some-repo")).thenThrow(new IOException());

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _commentCreateAction = new CommentCreateAction(_action);
        _commentCreateAction.run();

        Assertions.assertEquals(1, _eventQueue.size());
        Assertions.assertEquals(ConnectorConstants.CREATE_COMMENT_ACTION_FAILED, _eventQueue.poll().getType());
    }

    @Test
    @DisplayName("CommentCreateAction on exception 2")
    void testCommentCreateActionOnException2() throws IOException, InterruptedException {

        // Config
        Mockito.when(_config.getParent()).thenReturn("some-parent");
        Mockito.when(_config.getRepoName()).thenReturn("some-repo");
        Mockito.when(_config.getItemId()).thenReturn(10);
        Mockito.when(_config.getIssueType()).thenReturn("pullRequest");
        Mockito.when(_config.getBody()).thenReturn("some-body");
        Mockito.when(_config.getStartTime()).thenReturn((long)10);
        
        // Github 
        Mockito.when(_github.getRepository("some-parent/some-repo")).thenReturn(_repo);
        Mockito.when(_repo.getPullRequest(10)).thenThrow(new IOException());

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _commentCreateAction = new CommentCreateAction(_action);
        _commentCreateAction.run();

        Assertions.assertEquals(1, _eventQueue.size());
        Assertions.assertEquals(ConnectorConstants.CREATE_COMMENT_ACTION_FAILED, _eventQueue.poll().getType());
    }

    @Test
    @DisplayName("CommentCreateAction on exception 3")
    void testCommentCreateActionOnException3() throws IOException, InterruptedException {

        // Config
        Mockito.when(_config.getParent()).thenReturn("some-parent");
        Mockito.when(_config.getRepoName()).thenReturn("some-repo");
        Mockito.when(_config.getItemId()).thenReturn(10);
        Mockito.when(_config.getIssueType()).thenReturn("issue");
        Mockito.when(_config.getBody()).thenReturn("some-body");
        Mockito.when(_config.getStartTime()).thenReturn((long)10);
        
        // Github 
        Mockito.when(_github.getRepository("some-parent/some-repo")).thenReturn(_repo);
        Mockito.when(_repo.getIssue(10)).thenThrow(new IOException());

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _commentCreateAction = new CommentCreateAction(_action);
        _commentCreateAction.run();

        Assertions.assertEquals(1, _eventQueue.size());
        Assertions.assertEquals(ConnectorConstants.CREATE_COMMENT_ACTION_FAILED, _eventQueue.poll().getType());
    }

    
    @Test
    @DisplayName("CommentCreateAction issue on success")
    void testCommentCreateActionIssueOnSuccess() throws IOException, InterruptedException {

        // Config
        Mockito.when(_config.getParent()).thenReturn("some-parent");
        Mockito.when(_config.getRepoName()).thenReturn("some-repo");
        Mockito.when(_config.getItemId()).thenReturn(10);
        Mockito.when(_config.getIssueType()).thenReturn("issue");
        Mockito.when(_config.getBody()).thenReturn("some-body");
        Mockito.when(_config.getStartTime()).thenReturn((long)10);
        
        // Github 
        Mockito.when(_github.getRepository("some-parent/some-repo")).thenReturn(_repo);
        Mockito.when(_repo.getIssue(10)).thenReturn(_issue);

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _commentCreateAction = new CommentCreateAction(_action);
        _commentCreateAction.run();

        Assertions.assertEquals(1, _eventQueue.size());
        Assertions.assertEquals(ConnectorConstants.CREATE_COMMENT_ACTION_COMPLETED, _eventQueue.poll().getType());
    }

    @Test
    @DisplayName("CommentCreateAction pr on success")
    void testCommentCreateActionPrOnSuccess() throws IOException, InterruptedException {

        // Config
        Mockito.when(_config.getParent()).thenReturn("some-parent");
        Mockito.when(_config.getRepoName()).thenReturn("some-repo");
        Mockito.when(_config.getItemId()).thenReturn(10);
        Mockito.when(_config.getIssueType()).thenReturn("pullRequest");
        Mockito.when(_config.getBody()).thenReturn("some-body");
        Mockito.when(_config.getStartTime()).thenReturn((long)10);
        
        // Github 
        Mockito.when(_github.getRepository("some-parent/some-repo")).thenReturn(_repo);
        Mockito.when(_repo.getPullRequest(10)).thenReturn(_pr);

        // Action
        Mockito.when(_action.getActionCounter()).thenReturn(_counter);
        Mockito.when(_action.getActionErrorCounter()).thenReturn(_counter);
        Mockito.when(_action.getGitHub()).thenReturn(_github);
        Mockito.when(_action.getConfiguration()).thenReturn(_config);
        Mockito.when(_action.getConnector()).thenReturn(_connector);

        _commentCreateAction = new CommentCreateAction(_action);
        _commentCreateAction.run();

        Assertions.assertEquals(1, _eventQueue.size());
        Assertions.assertEquals(ConnectorConstants.CREATE_COMMENT_ACTION_COMPLETED, _eventQueue.poll().getType());
    }
}
