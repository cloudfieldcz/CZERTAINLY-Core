package com.czertainly.core.messaging.jms.producers;

import com.czertainly.api.model.common.events.data.InternalNotificationEventData;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.messaging.jms.configuration.MessagingProperties;
import com.czertainly.core.messaging.model.NotificationMessage;
import com.czertainly.core.messaging.model.NotificationRecipient;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@AllArgsConstructor
public class NotificationProducer {
    private static final Logger logger = LoggerFactory.getLogger(NotificationProducer.class);

    private final JmsTemplate jmsTemplate;
    private final MessagePostProcessor messagePostProcessor;
    private final MessagingProperties messagingProperties;

    public void sendMessage(final NotificationMessage notificationMessage) {
        jmsTemplate.convertAndSend(messagingProperties.destinationNotifications(), notificationMessage, messagePostProcessor);
    }

    public void produceMessage(final NotificationMessage notificationMessage) {
        if ((notificationMessage.getNotificationProfileUuids() == null || notificationMessage.getNotificationProfileUuids().isEmpty()) && (notificationMessage.getRecipients() == null || notificationMessage.getRecipients().isEmpty())) {
            logger.warn("Recipients for notification of event {} is empty. Message: {}", notificationMessage.getEvent().getLabel(), notificationMessage);
        } else {
            sendMessage(notificationMessage);
        }
    }

    public void produceInternalNotificationMessage(Resource resource, UUID resourceUUID, List<NotificationRecipient> recipients, String text, String detail) {
        produceMessage(new NotificationMessage(null,
                resource,
                resourceUUID,
                null,
                recipients,
                new InternalNotificationEventData(text, detail)));
    }
}
