package com.czertainly.core.messaging.jms.producers;

import com.czertainly.core.messaging.jms.configuration.MessagingProperties;
import com.czertainly.core.messaging.model.ActionMessage;
import lombok.AllArgsConstructor;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class ActionsProducer {
    private final JmsTemplate jmsTemplate;
    private final MessagePostProcessor messagePostProcessor;
    private final MessagingProperties messagingProperties;

    public void sendMessage(final ActionMessage actionMessage) {
        jmsTemplate.convertAndSend(messagingProperties.destinationActions(), actionMessage, messagePostProcessor);
    }

    public void produceMessage(final ActionMessage actionMessage) {
        sendMessage(actionMessage);
    }
}
