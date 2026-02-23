package com.czertainly.core.messaging.jms.configuration;

import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.core.credential.TokenCredential;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.jms.ConnectionFactory;
import org.apache.qpid.jms.JmsConnectionExtensions;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.slf4j.Logger;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;
import org.springframework.web.util.UriComponentsBuilder;

import static org.slf4j.LoggerFactory.getLogger;

@EnableJms
@Configuration
@EnableConfigurationProperties({MessagingProperties.class, MessagingConcurrencyProperties.class})
public class JmsConfig {
    private static final Logger logger = getLogger(JmsConfig.class);

    @Bean
    public ConnectionFactory connectionFactory(MessagingProperties props) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(props.getEffectiveBrokerUrl());

        // For RabbitMQ with AMQP 1.0, vhost is specified in the AMQP Open frame hostname field
        // The hostname field must be "vhost:name" format according to RabbitMQ AMQP 1.0 docs
        // We use amqp.vhost connection property to set this value
        if (props.brokerType() == MessagingProperties.BrokerType.RABBITMQ &&
                props.virtualHost() != null && !props.virtualHost().isEmpty()) {
            builder.queryParam("amqp.vhost", "vhost:" + props.virtualHost());
        }
        String brokerUrl = builder.build().toUriString();

        JmsConnectionFactory factory = new JmsConnectionFactory(brokerUrl);
        factory.setForceSyncSend(true);

        if (props.brokerType() == MessagingProperties.BrokerType.SERVICEBUS) {
            configureServiceBusAuthentication(factory, props);
            // Return raw factory for ServiceBus - listener containers manage their own connections.
            // CachingConnectionFactory interferes with DefaultMessageListenerContainer recovery
            // for durable/shared subscriptions. Producers get CachingConnectionFactory via jmsTemplate bean.
            return factory;
        }

        // RabbitMQ - standard username/password authentication
        logger.info("Connecting to RabbitMQ broker: {} with vhost: {}", props.getEffectiveBrokerUrl(), props.virtualHost());
        factory.setUsername(props.username());
        factory.setPassword(props.password());

        return factory;
    }

    private void configureServiceBusAuthentication(JmsConnectionFactory factory, MessagingProperties props) {
        if (props.aadAuth() != null && props.aadAuth().isEnabled()) {
            // AAD (Azure Active Directory) OAuth2 authentication
            logger.info("Connecting to Azure ServiceBus: {} using AAD authentication", props.getEffectiveBrokerUrl());

            TokenCredential credential = new ClientSecretCredentialBuilder()
                    .tenantId(props.aadAuth().tenantId())
                    .clientId(props.aadAuth().clientId())
                    .clientSecret(props.aadAuth().clientSecret())
                    .build();

            AadTokenProvider tokenProvider = new AadTokenProvider(credential, props.aadAuth().tokenRefreshInterval(), props.aadAuth().tokenGettingTimeout());

            // Azure ServiceBus requires "$jwt" username for OAuth2 token authentication
            factory.setUsername("$jwt");
            factory.setExtension(
                    JmsConnectionExtensions.PASSWORD_OVERRIDE.toString(),
                    tokenProvider
            );
        } else {
            // SAS (Shared Access Signature) token authentication
            logger.info("Connecting to Azure ServiceBus: {} using SAS authentication", props.getEffectiveBrokerUrl());
            factory.setUsername(props.username());
            factory.setPassword(props.password());
        }
    }

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            MessagingProperties messagingProperties) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        if (messagingProperties.brokerType() == MessagingProperties.BrokerType.SERVICEBUS) {
            factory.setPubSubDomain(true);
            factory.setSubscriptionDurable(true);
        }
        if (messagingProperties.listener() != null && messagingProperties.listener().recoveryInterval() != null) {
            factory.setRecoveryInterval(messagingProperties.listener().recoveryInterval());
        }
        return factory;
    }

    @Bean
    public MessageConverter messageConverter(ObjectMapper jacksonObjectMapper) {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(jacksonObjectMapper);
        converter.setTargetType(MessageType.TEXT);

        // Configure ObjectMapper with Java 8 date/time support
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        converter.setObjectMapper(objectMapper);

        return converter;
    }

    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory,
                                   MessageConverter messageConverter,
                                   MessagingProperties messagingProperties) {
        // Producers use CachingConnectionFactory for automatic reconnection when broker
        // forces connection close. This is separate from the listener ConnectionFactory —
        // DefaultMessageListenerContainer manages its own connections and should NOT use
        // CachingConnectionFactory (it interferes with container's recovery mechanism).
        CachingConnectionFactory producerFactory = new CachingConnectionFactory(connectionFactory);
        producerFactory.setSessionCacheSize(messagingProperties.sessionCacheSize());
        producerFactory.setReconnectOnException(true);

        JmsTemplate template = new JmsTemplate(producerFactory);
        template.setMessageConverter(messageConverter);
        if (messagingProperties.brokerType() == MessagingProperties.BrokerType.SERVICEBUS) {
            template.setPubSubDomain(true);
        }
        return template;
    }
}