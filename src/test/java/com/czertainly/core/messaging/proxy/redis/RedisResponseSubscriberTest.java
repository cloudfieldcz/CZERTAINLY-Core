package com.czertainly.core.messaging.proxy.redis;

import com.czertainly.api.clients.mq.model.ProxyResponse;
import com.czertainly.core.messaging.proxy.ProxyResponseCorrelator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Unit tests for {@link RedisResponseSubscriber}.
 * Tests handling of Redis pub/sub messages and completion of local pending requests.
 */
@ExtendWith(MockitoExtension.class)
class RedisResponseSubscriberTest {

    @Mock
    private ProxyResponseCorrelator correlator;

    @Mock
    private ObjectMapper objectMapper;

    private RedisResponseSubscriber subscriber;

    @BeforeEach
    void setUp() {
        subscriber = new RedisResponseSubscriber(correlator, objectMapper);
    }

    @Test
    void onMessage_deserializesAndTriesToCompleteLocally() throws Exception {
        String jsonMessage = "{\"correlationId\":\"corr-1\",\"statusCode\":200}";
        ProxyResponse response = createResponse("corr-1");
        when(objectMapper.readValue(jsonMessage, ProxyResponse.class)).thenReturn(response);
        when(correlator.tryCompleteRequest(response)).thenReturn(true);

        subscriber.onMessage(jsonMessage);

        verify(objectMapper).readValue(jsonMessage, ProxyResponse.class);
        verify(correlator).tryCompleteRequest(response);
    }

    @Test
    void onMessage_whenNotFoundLocally_ignoresQuietly() throws Exception {
        String jsonMessage = "{\"correlationId\":\"corr-other-instance\"}";
        ProxyResponse response = createResponse("corr-other-instance");
        when(objectMapper.readValue(jsonMessage, ProxyResponse.class)).thenReturn(response);
        when(correlator.tryCompleteRequest(response)).thenReturn(false);

        assertThatCode(() -> subscriber.onMessage(jsonMessage)).doesNotThrowAnyException();
        verify(correlator).tryCompleteRequest(response);
    }

    @Test
    void onMessage_onDeserializationError_doesNotCallCorrelator() throws Exception {
        String invalidJson = "invalid json";
        when(objectMapper.readValue(invalidJson, ProxyResponse.class))
                .thenThrow(new JsonProcessingException("Parse error") {});

        assertThatCode(() -> subscriber.onMessage(invalidJson)).doesNotThrowAnyException();

        verify(correlator, never()).tryCompleteRequest(any());
    }

    @Test
    void onMessage_withNullCorrelationId_skipsCorrelator() throws Exception {
        String jsonMessage = "{\"correlationId\":null,\"statusCode\":200}";
        ProxyResponse response = createResponse(null);
        when(objectMapper.readValue(jsonMessage, ProxyResponse.class)).thenReturn(response);

        subscriber.onMessage(jsonMessage);

        verify(correlator, never()).tryCompleteRequest(any());
    }

    @Test
    void onMessage_onCorrelatorException_handlesGracefully() throws Exception {
        String jsonMessage = "{\"correlationId\":\"corr-error\"}";
        ProxyResponse response = createResponse("corr-error");
        when(objectMapper.readValue(jsonMessage, ProxyResponse.class)).thenReturn(response);
        when(correlator.tryCompleteRequest(response)).thenThrow(new IllegalStateException("Correlator error"));

        assertThatCode(() -> subscriber.onMessage(jsonMessage)).doesNotThrowAnyException();
    }

    private ProxyResponse createResponse(String correlationId) {
        return ProxyResponse.builder()
                .correlationId(correlationId)
                .statusCode(200)
                .timestamp(Instant.now())
                .build();
    }
}
