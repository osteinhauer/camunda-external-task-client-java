/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.client.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.AbstractResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HttpContext;
import org.camunda.bpm.client.ExternalTaskClient;
import org.camunda.bpm.client.exception.ConnectionLostException;
import org.camunda.bpm.client.exception.NotAcquiredException;
import org.camunda.bpm.client.exception.NotFoundException;
import org.camunda.bpm.client.exception.NotResumedException;
import org.camunda.bpm.client.helper.ClosableHttpClientMock;
import org.camunda.bpm.client.helper.MockProvider;
import org.camunda.bpm.client.impl.EngineClient;
import org.camunda.bpm.client.impl.ExternalTaskClientBuilderImpl;
import org.camunda.bpm.client.task.impl.dto.BpmnErrorRequestDto;
import org.camunda.bpm.client.task.impl.dto.ExtendLockRequestDto;
import org.camunda.bpm.client.task.impl.dto.FailureRequestDto;
import org.camunda.bpm.client.topic.TopicSubscriptionBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Tassilo Weidner
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({HttpClients.class, ExternalTaskClientBuilderImpl.class})
@PowerMockIgnore("javax.net.ssl.*")
public class ExternalTaskServiceTest {

  private CloseableHttpClient httpClient;
  private CloseableHttpResponse closeableHttpResponse;

  @Before
  public void setUp() throws JsonProcessingException {
    mockStatic(HttpClients.class);

    HttpClientBuilder httpClientBuilderMock = mock(HttpClientBuilder.class, RETURNS_DEEP_STUBS);
    when(HttpClients.custom())
      .thenReturn(httpClientBuilderMock);

    closeableHttpResponse = mock(CloseableHttpResponse.class);
    when(closeableHttpResponse.getStatusLine())
      .thenReturn(mock(StatusLine.class));

    httpClient = spy(new ClosableHttpClientMock(closeableHttpResponse));
    when(httpClientBuilderMock.build())
      .thenReturn(httpClient);

    List<ExternalTask> externalTasks = Collections.singletonList(MockProvider.createExternalTask());
    ObjectMapper objectMapper = new ObjectMapper();
    byte[] externalTasksAsBytes = objectMapper.writeValueAsBytes(externalTasks);
    HttpEntity entity = new ByteArrayEntity(externalTasksAsBytes);
    doReturn(entity)
      .when(closeableHttpResponse).getEntity();
  }

  @Test
  public void shouldUnlockTask() throws IOException {
    // given
    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {
          externalTaskService.unlock(externalTask);
          handlerInvoked.set(true);
        });

    // when
    topicSubscriptionBuilder.open();

    while (!handlerInvoked.get()) {
      // sync
    }

    // then
    assertRequestPerformed(EngineClient.UNLOCK_RESOURCE_PATH);

    client.stop();
  }

  @Test
  public void shouldCompleteTask() throws IOException {
    // given
    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {
          externalTaskService.complete(externalTask);
          handlerInvoked.set(true);
        });

    // when
    topicSubscriptionBuilder.open();

    while (!handlerInvoked.get()) {
      // sync
    }

    // then
    assertRequestPerformed(EngineClient.COMPLETE_RESOURCE_PATH);

    client.stop();
  }

  @Test
  public void shouldHandleFailure() throws Exception {
    // given
    ObjectMapper objectMapper = spy(ObjectMapper.class);
    whenNew(ObjectMapper.class).withNoArguments().thenReturn(objectMapper);

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);

    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {
          externalTaskService.handleFailure(externalTask, MockProvider.ERROR_MESSAGE, MockProvider.ERROR_DETAILS,
            MockProvider.RETRIES, MockProvider.RETRY_TIMEOUT);
          handlerInvoked.set(true);
        });

    // when
    topicSubscriptionBuilder.open();

    while (!handlerInvoked.get()) {
      // sync
    }

    // then
    ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
    verify(objectMapper, atLeastOnce()).writeValueAsBytes(payloads.capture());

    for (Object request : payloads.getAllValues()) {
      if (request instanceof FailureRequestDto) {
        FailureRequestDto failureRequestDto = (FailureRequestDto) request;
        assertThat(failureRequestDto.getWorkerId().length()).isGreaterThan(35);
        assertThat(failureRequestDto.getErrorDetails()).isEqualTo(MockProvider.ERROR_DETAILS);
        assertThat(failureRequestDto.getErrorMessage()).isEqualTo(MockProvider.ERROR_MESSAGE);
        assertThat(failureRequestDto.getRetries()).isEqualTo(MockProvider.RETRIES);
        assertThat(failureRequestDto.getRetryTimeout()).isEqualTo(MockProvider.RETRY_TIMEOUT);
      }
    }

    assertRequestPerformed(EngineClient.FAILURE_RESOURCE_PATH);

    client.stop();
  }

  @Test
  public void shouldHandleBpmnError() throws Exception {
    // given
    ObjectMapper objectMapper = spy(ObjectMapper.class);
    whenNew(ObjectMapper.class).withNoArguments().thenReturn(objectMapper);

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {
          externalTaskService.handleBpmnError(externalTask, MockProvider.ERROR_CODE);
          handlerInvoked.set(true);
        });

    // when
    topicSubscriptionBuilder.open();

    while (!handlerInvoked.get()) {
      // sync
    }

    // then
    ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
    verify(objectMapper, atLeastOnce()).writeValueAsBytes(payloads.capture());

    for (Object request : payloads.getAllValues()) {
      if (request instanceof BpmnErrorRequestDto) {
        BpmnErrorRequestDto bpmnErrorRequestDto = (BpmnErrorRequestDto) request;
        assertThat(bpmnErrorRequestDto.getWorkerId().length()).isGreaterThan(35);
        assertThat(bpmnErrorRequestDto.getErrorCode()).isEqualTo(MockProvider.ERROR_CODE);
      }
    }

    assertRequestPerformed(EngineClient.BPMN_ERROR_RESOURCE_PATH);

    client.stop();
  }

  @Test
  public void shouldExtendLock() throws Exception {
    // given
    ObjectMapper objectMapper = spy(ObjectMapper.class);
    whenNew(ObjectMapper.class).withNoArguments().thenReturn(objectMapper);

    ExternalTaskClient client = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
    TopicSubscriptionBuilder topicSubscriptionBuilder =
      client.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {
          externalTaskService.extendLock(externalTask, MockProvider.NEW_DURATION);
          handlerInvoked.set(true);
        });

    // when
    topicSubscriptionBuilder.open();

    while (!handlerInvoked.get()) {
      // sync
    }

    // then
    ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
    verify(objectMapper, atLeastOnce()).writeValueAsBytes(payloads.capture());

    for (Object request : payloads.getAllValues()) {
      if (request instanceof ExtendLockRequestDto) {
        ExtendLockRequestDto extendLockRequestDto = (ExtendLockRequestDto) request;
        assertThat(extendLockRequestDto.getWorkerId().length()).isGreaterThan(35);
        assertThat(extendLockRequestDto.getNewDuration()).isEqualTo(MockProvider.NEW_DURATION);
      }
    }

    assertRequestPerformed(EngineClient.EXTEND_LOCK_RESOURCE_PATH);

    client.stop();
  }

  @Test
  public void shouldThrowNotFoundExceptionOnUnlockingTask() throws IOException {
    // given
    CloseableHttpClient httpClient = mockHttpResponseException(EngineClient.UNLOCK_RESOURCE_PATH, 404);

    ExternalTaskClient externalTaskClient = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final AtomicBoolean exceptionThrown = new AtomicBoolean(false);
    final List<NotFoundException> notFoundException = new ArrayList<>(); // list, as container must be final and changeable

    TopicSubscriptionBuilder topicSubscriptionBuilder =
      externalTaskClient.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {
          try {
            externalTaskService.unlock(externalTask);
          } catch (NotFoundException e) {
            notFoundException.add(e);
            exceptionThrown.set(true);
          }
        });

    // when
    topicSubscriptionBuilder.open();

    while (!exceptionThrown.get()) {
      // sync
    }

    // then
    assertThat(notFoundException.get(0).getMessage())
      .contains("Exception while unlocking the external task: The task could not be found");
    assertRequestPerformed(EngineClient.UNLOCK_RESOURCE_PATH, httpClient);

    externalTaskClient.stop();
  }

  @Test
  public void shouldThrowNotResumedExceptionOnCompletingTask() throws IOException {
    // given
    CloseableHttpClient httpClient = mockHttpResponseException(EngineClient.COMPLETE_RESOURCE_PATH, 500);

    ExternalTaskClient externalTaskClient = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final AtomicBoolean exceptionThrown = new AtomicBoolean(false);
    final List<NotResumedException> notResumedException = new ArrayList<>(); // list, as container must be final and changeable

    TopicSubscriptionBuilder topicSubscriptionBuilder =
      externalTaskClient.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {
          try {
            externalTaskService.complete(externalTask);
          } catch (NotResumedException e) {
            notResumedException.add(e);
            exceptionThrown.set(true);
          }
        });

    // when
    topicSubscriptionBuilder.open();

    while (!exceptionThrown.get()) {
      // sync
    }

    // then
    assertThat(notResumedException.get(0).getMessage())
      .contains("Exception while completing the external task: The corresponding process instance could not be resumed");
    assertRequestPerformed(EngineClient.COMPLETE_RESOURCE_PATH, httpClient);

    externalTaskClient.stop();
  }

  @Test
  public void shouldThrowNotAcquiredExceptionOnNotifyingTaskFailure() throws IOException {
    // given
    CloseableHttpClient httpClient = mockHttpResponseException(EngineClient.FAILURE_RESOURCE_PATH, 400);

    ExternalTaskClient externalTaskClient = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final AtomicBoolean exceptionThrown = new AtomicBoolean(false);
    final List<NotAcquiredException> notAcquiredException = new ArrayList<>(); // list, as container must be final and changeable

    TopicSubscriptionBuilder topicSubscriptionBuilder =
      externalTaskClient.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {
          try {
            externalTaskService.handleFailure(externalTask, MockProvider.ERROR_MESSAGE, MockProvider.ERROR_DETAILS, MockProvider.RETRIES, MockProvider.RETRY_TIMEOUT);
          } catch (NotAcquiredException e) {
            notAcquiredException.add(e);
            exceptionThrown.set(true);
          }
        });

    // when
    topicSubscriptionBuilder.open();

    while (!exceptionThrown.get()) {
      // sync
    }

    // then
    assertThat(notAcquiredException.get(0).getMessage())
      .contains("Exception while notifying a failure: The task's most recent lock could not be acquired");
    assertRequestPerformed(EngineClient.FAILURE_RESOURCE_PATH, httpClient);

    externalTaskClient.stop();
  }

  @Test
  public void shouldThrowConnectionLostExceptionOnNotifyingBpmnError() throws IOException {
    // given
    CloseableHttpClient httpClient = mockHttpResponseException(EngineClient.BPMN_ERROR_RESOURCE_PATH, null);

    ExternalTaskClient externalTaskClient = ExternalTaskClient.create()
      .baseUrl(MockProvider.BASE_URL)
      .build();

    final AtomicBoolean exceptionThrown = new AtomicBoolean(false); // list, as container must be final and changeable
    final List<ConnectionLostException> connectionLostException = new ArrayList<>();

    TopicSubscriptionBuilder topicSubscriptionBuilder =
      externalTaskClient.subscribe(MockProvider.TOPIC_NAME)
        .lockDuration(5000)
        .handler((externalTask, externalTaskService) -> {
          try {
            externalTaskService.handleBpmnError(externalTask, MockProvider.ERROR_CODE);
          } catch (ConnectionLostException e) {
            connectionLostException.add(e);
            exceptionThrown.set(true);
          }
        });

    // when
    topicSubscriptionBuilder.open();

    while (!exceptionThrown.get()) {
      // sync
    }

    // then
    assertThat(connectionLostException.get(0).getMessage())
      .contains("Exception while notifying a BPMN error: Connection could not be established");
    assertRequestPerformed(EngineClient.BPMN_ERROR_RESOURCE_PATH, httpClient);

    externalTaskClient.stop();
  }

  // helper ////////////////////////////////////////////////
  private void assertRequestPerformed(String resourcePath) throws IOException {
    assertRequestPerformed(resourcePath, httpClient);
  }

  private void assertRequestPerformed(String resourcePath, CloseableHttpClient httpClient) throws IOException {
    ArgumentCaptor<HttpUriRequest> argumentCaptor = ArgumentCaptor.forClass(HttpUriRequest.class);
    verify(httpClient, atLeastOnce()).execute(argumentCaptor.capture(), any(AbstractResponseHandler.class));

    String resourceUrl = MockProvider.BASE_URL + resourcePath;
    resourceUrl = resourceUrl.replace(EngineClient.ID_PATH_PARAM, MockProvider.ID);

    List<String> requestUrls = new ArrayList<String>();
    List<HttpUriRequest> requests = argumentCaptor.getAllValues();
    for (HttpUriRequest request : requests) {
      requestUrls.add(request.getURI().toString());
    }

    assertThat(requestUrls).contains(resourceUrl);
  }

  private CloseableHttpClient mockHttpResponseException(String resourcePath, Integer statusCode) {
    CloseableHttpClient httpClient = spy(new ClosableHttpClientMock(closeableHttpResponse) {
      @Override
      protected CloseableHttpResponse doExecute(HttpHost target, HttpRequest request, HttpContext context) throws IOException {
        String resourceUrl = MockProvider.BASE_URL + resourcePath;
        resourceUrl = resourceUrl.replace(EngineClient.ID_PATH_PARAM, MockProvider.ID);

        if (request.toString().equals("POST " + resourceUrl + " HTTP/1.1") && statusCode == null) {
          throw new ClientProtocolException();
        }

        if (request.toString().equals("POST " + resourceUrl + " HTTP/1.1")) {
          throw new HttpResponseException(statusCode, "Exception thrown!");
        }

        return closeableHttpResponse;
      }
    });

    mockStatic(HttpClients.class);

    HttpClientBuilder httpClientBuilderMock = mock(HttpClientBuilder.class, RETURNS_DEEP_STUBS);
    when(HttpClients.custom())
      .thenReturn(httpClientBuilderMock);

    when(httpClientBuilderMock.build())
      .thenReturn(httpClient);

    return httpClient;
  }

}
