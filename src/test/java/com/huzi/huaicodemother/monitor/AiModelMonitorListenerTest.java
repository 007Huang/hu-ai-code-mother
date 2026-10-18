package com.huzi.huaicodemother.monitor;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiModelMonitorListenerTest {

    private AiModelMonitorListener listener;
    private AiModelMetricsCollector metricsCollector;

    @BeforeEach
    void setUp() {
        listener = new AiModelMonitorListener();
        metricsCollector = mock(AiModelMetricsCollector.class);
        ReflectionTestUtils.setField(listener, "aiModelMetricsCollector", metricsCollector);
    }

    @AfterEach
    void tearDown() {
        MonitorContextHolder.clearContext();
    }

    @Test
    void skipsMonitoringWhenRequestContextIsMissing() {
        ChatModelRequestContext requestContext = mock(ChatModelRequestContext.class);
        when(requestContext.attributes()).thenReturn(new ConcurrentHashMap<>());

        assertDoesNotThrow(() -> listener.onRequest(requestContext));
        verify(metricsCollector, never()).recordRequest(any(), any(), any(), any());
    }

    @Test
    void usesCapturedContextForAsyncResponseCallback() {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatRequest chatRequest = mock(ChatRequest.class);
        when(chatRequest.modelName()).thenReturn("test-model");

        ChatModelRequestContext requestContext = mock(ChatModelRequestContext.class);
        when(requestContext.attributes()).thenReturn(attributes);
        when(requestContext.chatRequest()).thenReturn(chatRequest);

        MonitorContextHolder.setContext(MonitorContext.builder()
                .userId("100")
                .appId("200")
                .build());
        listener.onRequest(requestContext);
        MonitorContextHolder.clearContext();

        ChatModelResponseContext responseContext = mock(ChatModelResponseContext.class, RETURNS_DEEP_STUBS);
        when(responseContext.attributes()).thenReturn(attributes);
        when(responseContext.chatResponse().modelName()).thenReturn("test-model");
        when(responseContext.chatResponse().metadata().tokenUsage()).thenReturn(null);

        assertDoesNotThrow(() -> listener.onResponse(responseContext));
        verify(metricsCollector).recordRequest("100", "200", "test-model", "started");
        verify(metricsCollector).recordRequest("100", "200", "test-model", "success");
        verify(metricsCollector).recordResponseTime(
                org.mockito.ArgumentMatchers.eq("100"),
                org.mockito.ArgumentMatchers.eq("200"),
                org.mockito.ArgumentMatchers.eq("test-model"),
                any(Duration.class));
    }

    @Test
    void usesCapturedContextForAsyncErrorCallback() {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatRequest chatRequest = mock(ChatRequest.class);
        when(chatRequest.modelName()).thenReturn("test-model");

        ChatModelRequestContext requestContext = mock(ChatModelRequestContext.class);
        when(requestContext.attributes()).thenReturn(attributes);
        when(requestContext.chatRequest()).thenReturn(chatRequest);

        MonitorContextHolder.setContext(MonitorContext.builder()
                .userId("100")
                .appId("200")
                .build());
        listener.onRequest(requestContext);
        MonitorContextHolder.clearContext();

        ChatModelErrorContext errorContext = mock(ChatModelErrorContext.class);
        when(errorContext.attributes()).thenReturn(attributes);
        when(errorContext.chatRequest()).thenReturn(chatRequest);
        when(errorContext.error()).thenReturn(new IllegalStateException("test error"));

        assertDoesNotThrow(() -> listener.onError(errorContext));
        verify(metricsCollector).recordRequest("100", "200", "test-model", "started");
        verify(metricsCollector).recordRequest("100", "200", "test-model", "error");
        verify(metricsCollector).recordError("100", "200", "test-model", "test error");
        verify(metricsCollector).recordResponseTime(
                org.mockito.ArgumentMatchers.eq("100"),
                org.mockito.ArgumentMatchers.eq("200"),
                org.mockito.ArgumentMatchers.eq("test-model"),
                any(Duration.class));
    }

    @Test
    void skipsResponseAndErrorMonitoringWhenCapturedContextIsMissing() {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();

        ChatModelResponseContext responseContext = mock(ChatModelResponseContext.class);
        when(responseContext.attributes()).thenReturn(attributes);

        ChatModelErrorContext errorContext = mock(ChatModelErrorContext.class);
        when(errorContext.attributes()).thenReturn(attributes);
        when(errorContext.error()).thenReturn(new IllegalStateException("test error"));

        assertDoesNotThrow(() -> listener.onResponse(responseContext));
        assertDoesNotThrow(() -> listener.onError(errorContext));
        verify(metricsCollector, never()).recordRequest(any(), any(), any(), any());
    }
}
