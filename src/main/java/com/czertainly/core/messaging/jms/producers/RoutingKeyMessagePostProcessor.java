package com.czertainly.core.messaging.jms.producers;

import com.czertainly.core.messaging.jms.configuration.MessagingProperties;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import lombok.AllArgsConstructor;
import org.springframework.jms.core.MessagePostProcessor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * A message post-processor that adds a routing key property to JMS messages.
 * This class implements the {@link MessagePostProcessor} interface
 * and ensures that the "routingKey" property is set on outgoing messages before they are sent.

 * The routing key value is retrieved from the application configuration using the property
 * "spring.messaging.message.routing-key". If the property is not defined, a default value
 * of "scheduler" is used.
 */
@Component
@AllArgsConstructor
public class RoutingKeyMessagePostProcessor implements MessagePostProcessor {

    private final MessagingProperties messagingProperties;

    @Override
    public @NonNull Message postProcessMessage(Message message) throws JMSException {
        message.setStringProperty("routingKey", messagingProperties.routingKey().scheduler());
        return message;
    }
}
