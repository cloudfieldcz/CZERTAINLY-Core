package com.czertainly.core.messaging.jms.listeners.auditlogs;

import com.czertainly.core.messaging.jms.configuration.MessagingConcurrencyProperties;
import com.czertainly.core.messaging.jms.configuration.MessagingProperties;
import com.czertainly.core.messaging.jms.listeners.AbstractJmsEndpointConfig;
import com.czertainly.core.messaging.model.AuditLogMessage;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;
import org.springframework.stereotype.Component;


@Component
@Profile("!test")
@AllArgsConstructor
public class AuditLogsJmsEndpointConfig extends AbstractJmsEndpointConfig<AuditLogMessage> {
    private final MessagingProperties messagingProperties;
    private final MessagingConcurrencyProperties messagingConcurrencyProperties;

//    @Autowired
//    public void setMessageConverter(MessageConverter messageConverter) {
//        this.messageConverter = messageConverter;
//    }
//
//    @Autowired
//    public void setListenerMessageProcessor(MessageProcessor<AuditLogMessage> listenerMessageProcessor) {
//        this.listenerMessageProcessor = listenerMessageProcessor;
//    }
//    @Autowired
//    public void setJmsRetryTemplate(RetryTemplate jmsRetryTemplate) {
//        this.jmsRetryTemplate = jmsRetryTemplate;
//    }

    @Override
    public SimpleJmsListenerEndpoint listenerEndpoint() {
        return listenerEndpointInternal(
                () -> "auditLogsListener",
                messagingProperties::destinationAuditLogs,
                () -> messagingProperties.routingKey().auditLogs(),
                messagingConcurrencyProperties::auditLogs,
                AuditLogMessage.class
        );
    }
}
