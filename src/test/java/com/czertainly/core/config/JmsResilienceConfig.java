package com.czertainly.core.config;

import com.czertainly.core.messaging.jms.configuration.JmsConfig;
import com.czertainly.core.messaging.jms.configuration.MessagingConcurrencyProperties;
import com.czertainly.core.messaging.jms.configuration.MessagingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Profile;

@TestConfiguration
@Profile("toxiproxy-messaging-int-test | messaging-int-test")
@EnableConfigurationProperties({MessagingProperties.class, MessagingConcurrencyProperties.class})
public class JmsResilienceConfig extends JmsConfig {

}