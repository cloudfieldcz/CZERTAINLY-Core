package com.czertainly.core.messaging.jms.producers;

import com.czertainly.core.messaging.jms.configuration.MessagingProperties;
import com.czertainly.core.messaging.model.EventMessage;
import lombok.AllArgsConstructor;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class EventProducer {
    private final JmsTemplate jmsTemplate;
    private final MessagePostProcessor messagePostProcessor;
    private final MessagingProperties messagingProperties;

    public void sendMessage(final EventMessage eventMessage) {
        jmsTemplate.convertAndSend(messagingProperties.destinationEvent(), eventMessage, messagePostProcessor);
    }

    public void produceMessage(final EventMessage actionMessage) {
        sendMessage(actionMessage);
    }
}
