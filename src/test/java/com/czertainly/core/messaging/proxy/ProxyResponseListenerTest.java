package com.czertainly.core.messaging.proxy;

import com.czertainly.api.clients.mq.model.ProxyResponse;
import com.czertainly.api.exception.MessageHandlingException;
import com.czertainly.core.messaging.proxy.handler.MessageTypeHandlerRegistry;
import com.czertainly.core.messaging.proxy.redis.RedisResponseDistributor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ProxyResponseListener}.
 * Tests the three-tier response routing: handler → correlator → Redis distribution.
 */
@ExtendWith(MockitoExtension.class)
class ProxyResponseListenerTest {

    @Mock
    private ProxyResponseCorrelator correlator;

    @Mock
    private MessageTypeHandlerRegistry handlerRegistry;

    @Mock
    private RedisResponseDistributor redisDistributor;

    private ProxyResponseListener listener;

    @BeforeEach
    void setUp() {
        listener = new ProxyResponseListener(correlator, handlerRegistry, redisDistributor);
    }

    // ==================== Null Handling Tests ====================

    @Test
    void processMessage_withNullResponse_logsWarningAndReturns() {
        // Should not throw
        assertThatCode(() -> listener.processMessage(null)).doesNotThrowAnyException();

        verifyNoInteractions(handlerRegistry);
        verifyNoInteractions(correlator);
        verifyNoInteractions(redisDistributor);
    }

    // ==================== Handler-Based Routing (Tier 1) Tests ====================

    @Test
    void processMessage_withRegisteredHandler_dispatchesToHandler() throws MessageHandlingException {
        ProxyResponse response = createResponse("corr-1", "certificate.issued");
        when(handlerRegistry.hasHandler("certificate.issued")).thenReturn(true);
        when(handlerRegistry.dispatch(response)).thenReturn(true);

        listener.processMessage(response);

        verify(handlerRegistry).hasHandler("certificate.issued");
        verify(handlerRegistry).dispatch(response);
    }

    @Test
    void processMessage_withRegisteredHandler_doesNotCallCorrelator() throws MessageHandlingException {
        ProxyResponse response = createResponse("corr-1", "certificate.issued");
        when(handlerRegistry.hasHandler("certificate.issued")).thenReturn(true);
        when(handlerRegistry.dispatch(response)).thenReturn(true);

        listener.processMessage(response);

        verify(correlator, never()).tryCompleteRequest(any());
        verify(redisDistributor, never()).publishResponse(any());
    }

    @Test
    void processMessage_handlerNotFound_fallsToCorrelator() throws MessageHandlingException {
        ProxyResponse response = createResponse("corr-1", "unknown.type");
        when(handlerRegistry.hasHandler("unknown.type")).thenReturn(false);
        when(correlator.tryCompleteRequest(response)).thenReturn(true);

        listener.processMessage(response);

        verify(correlator).tryCompleteRequest(response);
    }

    @Test
    void processMessage_withNullHandlerRegistry_skipsHandlerCheck() throws MessageHandlingException {
        listener = new ProxyResponseListener(correlator, null, redisDistributor);
        ProxyResponse response = createResponse("corr-1", "some.type");
        when(correlator.tryCompleteRequest(response)).thenReturn(true);

        listener.processMessage(response);

        verify(correlator).tryCompleteRequest(response);
    }

    @Test
    void processMessage_withNullMessageType_skipsHandlerCheck() throws MessageHandlingException {
        ProxyResponse response = createResponse("corr-1", null);
        when(correlator.tryCompleteRequest(response)).thenReturn(true);

        listener.processMessage(response);

        // Handler check should be skipped when messageType is null
        verify(handlerRegistry, never()).hasHandler(any());
        verify(correlator).tryCompleteRequest(response);
    }

    // ==================== Correlator-Based Routing (Tier 2) Tests ====================

    @Test
    void processMessage_withLocalCorrelation_completesLocally() throws MessageHandlingException {
        ProxyResponse response = createResponse("corr-local", "GET:/v1/test");
        when(handlerRegistry.hasHandler("GET:/v1/test")).thenReturn(false);
        when(correlator.tryCompleteRequest(response)).thenReturn(true);

        listener.processMessage(response);

        verify(correlator).tryCompleteRequest(response);
        verify(redisDistributor, never()).publishResponse(any());
    }

    @Test
    void processMessage_locallyHandled_doesNotDistributeViaRedis() throws MessageHandlingException {
        ProxyResponse response = createResponse("corr-local", "POST:/v1/data");
        when(handlerRegistry.hasHandler("POST:/v1/data")).thenReturn(false);
        when(correlator.tryCompleteRequest(response)).thenReturn(true);

        listener.processMessage(response);

        verify(redisDistributor, never()).publishResponse(any());
    }

    // ==================== Redis Distribution (Tier 3) Tests ====================

    @Test
    void processMessage_notFoundLocally_distributesViaRedis() throws MessageHandlingException {
        ProxyResponse response = createResponse("corr-remote", "GET:/v1/resource");
        when(handlerRegistry.hasHandler("GET:/v1/resource")).thenReturn(false);
        when(correlator.tryCompleteRequest(response)).thenReturn(false);

        listener.processMessage(response);

        verify(redisDistributor).publishResponse(response);
    }

    @Test
    void processMessage_noRedisDistributor_logsWarning() throws MessageHandlingException {
        listener = new ProxyResponseListener(correlator, handlerRegistry, null);
        ProxyResponse response = createResponse("corr-remote", "GET:/v1/resource");
        when(handlerRegistry.hasHandler("GET:/v1/resource")).thenReturn(false);
        when(correlator.tryCompleteRequest(response)).thenReturn(false);

        // Should not throw, just log warning
        assertThatCode(() -> listener.processMessage(response)).doesNotThrowAnyException();
    }

    @Test
    void processMessage_withNullCorrelationId_logsWarningAndReturns() throws MessageHandlingException {
        ProxyResponse response = createResponse(null, "GET:/v1/test");
        when(handlerRegistry.hasHandler("GET:/v1/test")).thenReturn(false);

        // Should not throw, just log warning
        assertThatCode(() -> listener.processMessage(response)).doesNotThrowAnyException();

        verify(correlator, never()).tryCompleteRequest(any());
        verify(redisDistributor, never()).publishResponse(any());
    }

    @Test
    void processMessage_withBlankCorrelationId_logsWarningAndReturns() throws MessageHandlingException {
        ProxyResponse response = createResponse("   ", "GET:/v1/test");
        when(handlerRegistry.hasHandler("GET:/v1/test")).thenReturn(false);

        // Should not throw, just log warning
        assertThatCode(() -> listener.processMessage(response)).doesNotThrowAnyException();

        verify(correlator, never()).tryCompleteRequest(any());
        verify(redisDistributor, never()).publishResponse(any());
    }

    // ==================== Error Handling Tests ====================

    @Test
    void processMessage_onHandlerException_throwsMessageHandlingException() {
        ProxyResponse response = createResponse("corr-1", "error.type");
        when(handlerRegistry.hasHandler("error.type")).thenReturn(true);
        when(handlerRegistry.dispatch(response)).thenThrow(new RuntimeException("Handler failed"));

        assertThatThrownBy(() -> listener.processMessage(response))
                .isInstanceOf(MessageHandlingException.class)
                .hasMessageContaining("Failed to process proxy response");
    }

    @Test
    void processMessage_onCorrelatorException_throwsMessageHandlingException() {
        ProxyResponse response = createResponse("corr-1", "test.type");
        when(handlerRegistry.hasHandler("test.type")).thenReturn(false);
        when(correlator.tryCompleteRequest(response)).thenThrow(new RuntimeException("Correlator failed"));

        assertThatThrownBy(() -> listener.processMessage(response))
                .isInstanceOf(MessageHandlingException.class)
                .hasMessageContaining("Failed to process proxy response");
    }

    @Test
    void processMessage_onRedisException_throwsMessageHandlingException() {
        ProxyResponse response = createResponse("corr-1", "test.type");
        when(handlerRegistry.hasHandler("test.type")).thenReturn(false);
        when(correlator.tryCompleteRequest(response)).thenReturn(false);
        doThrow(new RuntimeException("Redis failed")).when(redisDistributor).publishResponse(response);

        assertThatThrownBy(() -> listener.processMessage(response))
                .isInstanceOf(MessageHandlingException.class)
                .hasMessageContaining("Failed to process proxy response");
    }

    // ==================== Helper Methods ====================

    private ProxyResponse createResponse(String correlationId, String messageType) {
        return ProxyResponse.builder()
                .correlationId(correlationId)
                .messageType(messageType)
                .statusCode(200)
                .timestamp(Instant.now())
                .build();
    }
}
