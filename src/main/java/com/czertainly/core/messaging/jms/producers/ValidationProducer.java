package com.czertainly.core.messaging.jms.producers;

import com.czertainly.core.messaging.jms.configuration.MessagingProperties;
import com.czertainly.core.messaging.model.ValidationMessage;
import lombok.AllArgsConstructor;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class ValidationProducer {
    private final JmsTemplate jmsTemplate;
    private final MessagePostProcessor messagePostProcessor;
    private final MessagingProperties messagingProperties;

    public void sendMessage(final ValidationMessage validationMessage) {
        jmsTemplate.convertAndSend(messagingProperties.destinationActions(), validationMessage, messagePostProcessor);
    }

    public void produceMessage(final ValidationMessage actionMessage) {
        sendMessage(actionMessage);
    }
}
