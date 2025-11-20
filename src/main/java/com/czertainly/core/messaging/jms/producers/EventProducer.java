package com.czertainly.core.messaging.jms.producers;

import com.czertainly.core.messaging.jms.configuration.JmsConfig;
import com.czertainly.core.messaging.jms.configuration.MessagingProperties;
import com.czertainly.core.messaging.model.EventMessage;
import lombok.AllArgsConstructor;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class EventProducer {
    private final JmsTemplate jmsTemplate;
    private final MessagingProperties messagingProperties;
    private final RetryTemplate retryTemplate;

    public void sendMessage(final EventMessage eventMessage) {
        retryTemplate.execute(context -> {
            jmsTemplate.convertAndSend(
                    messagingProperties.produceDestinationEvent(),
                    eventMessage,
                    message -> {
                        message.setStringProperty(JmsConfig.ROUTING_KEY, messagingProperties.routingKey().event());
                        return message;
                    });
            return null;
        });
    }

    public void produceMessage(final EventMessage eventMessage) {
        sendMessage(eventMessage);
    }
}
