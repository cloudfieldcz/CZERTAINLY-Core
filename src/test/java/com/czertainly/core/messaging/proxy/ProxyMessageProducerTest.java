package com.czertainly.core.messaging.proxy;

import com.czertainly.api.clients.mq.model.ConnectorRequest;
import com.czertainly.api.clients.mq.model.ProxyRequest;
import com.czertainly.core.messaging.jms.configuration.MessagingProperties;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ProxyMessageProducer}.
 * Tests message sending with different broker configurations.
 */
@ExtendWith(MockitoExtension.class)
class ProxyMessageProducerTest {

    @Mock
    private JmsTemplate jmsTemplate;

    @Mock
    private MessagingProperties messagingProperties;

    @Mock
    private RetryTemplate retryTemplate;

    @Captor
    private ArgumentCaptor<MessagePostProcessor> postProcessorCaptor;

    private ProxyProperties proxyProperties;
    private ProxyMessageProducer producer;

    @BeforeEach
    void setUp() throws Exception {
        proxyProperties = new ProxyProperties(
                "czertainly-proxy",  // exchange
                "core",              // responseQueue
                Duration.ofSeconds(30),
                1000,
                null
        );

        // Default: execute callback immediately for RetryTemplate
        when(retryTemplate.execute(any())).thenAnswer(invocation -> {
            RetryCallback<?, ?> callback = invocation.getArgument(0);
            return callback.doWithRetry(null);
        });

        producer = new ProxyMessageProducer(jmsTemplate, proxyProperties, messagingProperties, retryTemplate);
    }

    // ==================== ServiceBus Tests ====================

    @Test
    void send_withServiceBus_usesTopicDirectly() throws JMSException {
        when(messagingProperties.name()).thenReturn(MessagingProperties.BrokerName.SERVICEBUS);

        ProxyRequest request = createProxyRequest("corr-1");
        producer.send(request, "proxy-001");

        verify(jmsTemplate).convertAndSend(
                eq("czertainly-proxy"),
                eq(request),
                any(MessagePostProcessor.class)
        );
    }

    @Test
    void send_withServiceBus_setsJMSTypeToRoutingKey() throws JMSException {
        when(messagingProperties.name()).thenReturn(MessagingProperties.BrokerName.SERVICEBUS);

        ProxyRequest request = createProxyRequest("corr-1");
        producer.send(request, "proxy-001");

        verify(jmsTemplate).convertAndSend(
                any(String.class),
                eq(request),
                postProcessorCaptor.capture()
        );

        // Verify the post processor sets JMSType correctly
        Message mockMessage = mock(Message.class);
        postProcessorCaptor.getValue().postProcessMessage(mockMessage);

        verify(mockMessage).setJMSType("request.proxy-001");
    }

    @Test
    void send_withServiceBus_setsJMSCorrelationID() throws JMSException {
        when(messagingProperties.name()).thenReturn(MessagingProperties.BrokerName.SERVICEBUS);

        ProxyRequest request = createProxyRequest("my-correlation-id");
        producer.send(request, "proxy-001");

        verify(jmsTemplate).convertAndSend(
                any(String.class),
                eq(request),
                postProcessorCaptor.capture()
        );

        Message mockMessage = mock(Message.class);
        postProcessorCaptor.getValue().postProcessMessage(mockMessage);

        verify(mockMessage).setJMSCorrelationID("my-correlation-id");
    }

    // ==================== RabbitMQ Tests ====================

    @Test
    void send_withRabbitMQ_prefixesExchange() throws JMSException {
        when(messagingProperties.name()).thenReturn(MessagingProperties.BrokerName.RABBITMQ);
        when(messagingProperties.exchangePrefix()).thenReturn("/exchanges/");

        ProxyRequest request = createProxyRequest("corr-1");
        producer.send(request, "proxy-002");

        verify(jmsTemplate).convertAndSend(
                eq("/exchanges/czertainly-proxy"),
                eq(request),
                any(MessagePostProcessor.class)
        );
    }

    @Test
    void send_withRabbitMQNoExchangePrefix_usesTopicOnly() throws JMSException {
        when(messagingProperties.name()).thenReturn(MessagingProperties.BrokerName.RABBITMQ);
        when(messagingProperties.exchangePrefix()).thenReturn(null);

        ProxyRequest request = createProxyRequest("corr-1");
        producer.send(request, "proxy-003");

        verify(jmsTemplate).convertAndSend(
                eq("czertainly-proxy"),
                eq(request),
                any(MessagePostProcessor.class)
        );
    }

    @Test
    void send_withRabbitMQ_setsCorrectRoutingKey() throws JMSException {
        when(messagingProperties.name()).thenReturn(MessagingProperties.BrokerName.RABBITMQ);
        when(messagingProperties.exchangePrefix()).thenReturn("/exchanges/");

        ProxyRequest request = createProxyRequest("corr-1");
        producer.send(request, "my-proxy-instance");

        verify(jmsTemplate).convertAndSend(
                any(String.class),
                eq(request),
                postProcessorCaptor.capture()
        );

        Message mockMessage = mock(Message.class);
        postProcessorCaptor.getValue().postProcessMessage(mockMessage);

        verify(mockMessage).setJMSType("request.my-proxy-instance");
    }

    // ==================== Retry Tests ====================

    @Test
    void send_usesRetryTemplate() throws Exception {
        when(messagingProperties.name()).thenReturn(MessagingProperties.BrokerName.SERVICEBUS);

        ProxyRequest request = createProxyRequest("corr-1");
        producer.send(request, "proxy-001");

        verify(retryTemplate).execute(any());
        verify(jmsTemplate).convertAndSend(
                any(String.class),
                eq(request),
                any(MessagePostProcessor.class)
        );
    }

    // ==================== Different ProxyId Tests ====================

    @Test
    void send_withDifferentProxyIds_usesCorrectRoutingKey() throws JMSException {
        when(messagingProperties.name()).thenReturn(MessagingProperties.BrokerName.SERVICEBUS);

        // First request
        producer.send(createProxyRequest("corr-1"), "proxy-alpha");

        verify(jmsTemplate).convertAndSend(
                any(String.class),
                any(ProxyRequest.class),
                postProcessorCaptor.capture()
        );

        Message mockMessage1 = mock(Message.class);
        postProcessorCaptor.getValue().postProcessMessage(mockMessage1);
        verify(mockMessage1).setJMSType("request.proxy-alpha");

        // Reset for second request
        reset(jmsTemplate);

        // Second request with different proxyId
        producer.send(createProxyRequest("corr-2"), "proxy-beta");

        verify(jmsTemplate).convertAndSend(
                any(String.class),
                any(ProxyRequest.class),
                postProcessorCaptor.capture()
        );

        Message mockMessage2 = mock(Message.class);
        postProcessorCaptor.getValue().postProcessMessage(mockMessage2);
        verify(mockMessage2).setJMSType("request.proxy-beta");
    }

    @Test
    void send_preservesRequestCorrelationId() throws JMSException {
        when(messagingProperties.name()).thenReturn(MessagingProperties.BrokerName.SERVICEBUS);

        ProxyRequest request = createProxyRequest("unique-correlation-123");
        producer.send(request, "proxy-001");

        verify(jmsTemplate).convertAndSend(
                any(String.class),
                eq(request),
                postProcessorCaptor.capture()
        );

        Message mockMessage = mock(Message.class);
        postProcessorCaptor.getValue().postProcessMessage(mockMessage);

        verify(mockMessage).setJMSCorrelationID("unique-correlation-123");
    }

    // ==================== Helper Methods ====================

    private ProxyRequest createProxyRequest(String correlationId) {
        return ProxyRequest.builder()
                .correlationId(correlationId)
                .messageType("POST:/v1/test")
                .timestamp(Instant.now())
                .connectorRequest(ConnectorRequest.builder()
                        .connectorUrl("http://connector.example.com")
                        .method("POST")
                        .path("/v1/test")
                        .timeout("30s")
                        .build())
                .build();
    }
}
