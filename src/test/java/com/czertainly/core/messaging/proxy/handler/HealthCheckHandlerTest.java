package com.czertainly.core.messaging.proxy.handler;

import com.czertainly.api.clients.mq.model.ProxyMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link HealthCheckHandler}.
 * Tests health check message handling.
 */
class HealthCheckHandlerTest {

    private HealthCheckHandler handler;

    @BeforeEach
    void setUp() {
        handler = new HealthCheckHandler();
    }

    @Test
    void handleResponse_withValidMessage_logsWithoutError() {
        ProxyMessage message = ProxyMessage.builder()
                .proxyId("proxy-001")
                .messageType("health.check")
                .timestamp(Instant.now())
                .build();

        // Should not throw any exception
        assertThatCode(() -> handler.handleResponse(message)).doesNotThrowAnyException();
    }

    @Test
    void handleResponse_withNullProxyId_handlesGracefully() {
        ProxyMessage message = ProxyMessage.builder()
                .proxyId(null)
                .messageType("health.check")
                .timestamp(Instant.now())
                .build();

        assertThatCode(() -> handler.handleResponse(message)).doesNotThrowAnyException();
    }

    @Test
    void handleResponse_withNullTimestamp_handlesGracefully() {
        ProxyMessage message = ProxyMessage.builder()
                .proxyId("proxy-001")
                .messageType("health.check")
                .timestamp(null)
                .build();

        assertThatCode(() -> handler.handleResponse(message)).doesNotThrowAnyException();
    }
}
