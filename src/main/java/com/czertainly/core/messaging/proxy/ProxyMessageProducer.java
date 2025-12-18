package com.czertainly.core.messaging.proxy;

import com.czertainly.api.clients.mq.model.ProxyRequest;
import com.czertainly.core.messaging.jms.configuration.MessagingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

/**
 * Produces proxy request messages to the message queue.
 * Sends requests to the appropriate proxy instance based on proxyId.
 */
@Slf4j
@Component
public class ProxyMessageProducer {

    private final JmsTemplate jmsTemplate;
    private final ProxyProperties proxyProperties;
    private final MessagingProperties messagingProperties;
    private final RetryTemplate retryTemplate;

    public ProxyMessageProducer(
            JmsTemplate jmsTemplate,
            ProxyProperties proxyProperties,
            MessagingProperties messagingProperties,
            RetryTemplate retryTemplate) {
        this.jmsTemplate = jmsTemplate;
        this.proxyProperties = proxyProperties;
        this.messagingProperties = messagingProperties;
        this.retryTemplate = retryTemplate;
        log.info("ProxyMessageProducer initialized with exchange: {}", proxyProperties.exchange());
    }

    /**
     * Send a proxy request to the specified proxy instance.
     *
     * @param request The proxy request to send
     * @param proxyId The target proxy instance ID
     */
    public void send(ProxyRequest request, String proxyId) {
        String routingKey = proxyProperties.getRequestRoutingKey(proxyId);
        String destination = getDestination();

        log.debug("Sending proxy request correlationId={} proxyId={} destination={} routingKey={}",
                request.getCorrelationId(), proxyId, destination, routingKey);

        retryTemplate.execute(context -> {
            jmsTemplate.convertAndSend(
                    destination,
                    request,
                    message -> {
                        // Azure-native: JMSType maps to Service Bus Label/Subject
                        // Use Label for routing - optimal with Correlation Filters
                        message.setJMSType(routingKey);
                        // Set JMS correlation ID for request/response matching
                        message.setJMSCorrelationID(request.getCorrelationId());
                        return message;
                    });

            log.debug("Sent proxy request correlationId={} routingKey={}",
                    request.getCorrelationId(), routingKey);
            return null;
        });
    }

    /**
     * Get the destination (topic/exchange) based on broker type.
     * For ServiceBus, we use the topic directly.
     * For RabbitMQ, we prefix with the exchange if configured.
     */
    private String getDestination() {
        if (messagingProperties.name() == MessagingProperties.BrokerName.SERVICEBUS) {
            return proxyProperties.exchange();
        }

        // For RabbitMQ, include exchange prefix if configured
        String exchangePrefix = messagingProperties.exchangePrefix();
        if (exchangePrefix != null) {
            return exchangePrefix + proxyProperties.exchange();
        }
        return proxyProperties.exchange();
    }
}
