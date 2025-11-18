package com.czertainly.core.messaging.jms.producers;

import com.czertainly.core.messaging.jms.configuration.MessagingProperties;
import com.czertainly.core.messaging.model.AuditLogMessage;
import lombok.AllArgsConstructor;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class AuditLogsProducer {
    private final JmsTemplate jmsTemplate;
    private final MessagePostProcessor messagePostProcessor;
    private final MessagingProperties messagingProperties;

    public void sendMessage(final AuditLogMessage auditLogMessage) {
        jmsTemplate.convertAndSend(messagingProperties.destinationAuditLogs(), auditLogMessage, messagePostProcessor);
    }

    public void produceMessage(final AuditLogMessage actionMessage) {
        sendMessage(actionMessage);
    }
}
