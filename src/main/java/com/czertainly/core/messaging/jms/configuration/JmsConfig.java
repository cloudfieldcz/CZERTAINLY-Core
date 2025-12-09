package com.czertainly.core.messaging.jms.configuration;

import jakarta.jms.ConnectionFactory;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

@EnableJms
@Configuration
@Profile("!test & !toxiproxy-messaging-int-test")
@EnableConfigurationProperties({MessagingProperties.class, MessagingConcurrencyProperties.class})
public class JmsConfig {

    public static final String ROUTING_KEY = "routingKey";

    @Bean
    public ConnectionFactory connectionFactory(MessagingProperties props) {
        JmsConnectionFactory factory = new JmsConnectionFactory(props.brokerUrl());
        factory.setUsername(props.user());
        factory.setPassword(props.password());
        factory.setForceSyncSend(true);
        return factory;
    }

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter, MessagingProperties messagingProperties) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        if (messagingProperties.name() == MessagingProperties.BrokerName.SERVICEBUS) {
            factory.setPubSubDomain(true);
            factory.setSubscriptionDurable(true);
        }
        if (messagingProperties.listener() != null && messagingProperties.listener().recoveryInterval() != null) {
            factory.setRecoveryInterval(messagingProperties.listener().recoveryInterval());
        }
        return factory;
    }

    @Bean
    public MessageConverter messageConverter() {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setTargetType(MessageType.TEXT);
        return converter;
    }

    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory,
                                   MessageConverter messageConverter) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}