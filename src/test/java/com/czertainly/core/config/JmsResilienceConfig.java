package com.czertainly.core.config;

import com.czertainly.core.messaging.jms.configuration.MessagingConcurrencyProperties;
import com.czertainly.core.messaging.jms.configuration.MessagingProperties;
import jakarta.jms.ConnectionFactory;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

import static org.slf4j.LoggerFactory.getLogger;

@TestConfiguration
@Profile("toxiproxy-messaging-int-test | messaging-int-test")
@EnableConfigurationProperties({MessagingProperties.class, MessagingConcurrencyProperties.class})
public class JmsResilienceConfig {

    private static final Logger logger = getLogger(JmsResilienceConfig.class);

    @Bean
    public ConnectionFactory connectionFactory(
            @Value("${spring.messaging.broker-url}") String brokerUrl,
            MessagingProperties props) {
        JmsConnectionFactory factory = new JmsConnectionFactory(brokerUrl);
        factory.setUsername(props.user());
        factory.setPassword(props.password());
        factory.setForceSyncSend(true);

        CachingConnectionFactory cachingFactory = new CachingConnectionFactory(factory);

        cachingFactory.setExceptionListener(exception -> {
            logger.error("JMS Exception detected: {}", exception.getMessage(), exception);
        });

        cachingFactory.setSessionCacheSize(1);

        return cachingFactory;
    }

    @Bean
    public MessageConverter messageConverter() {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName("_typeId");
        return converter;
    }

    @Bean
    @Primary
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory,
                                   MessageConverter messageConverter) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}