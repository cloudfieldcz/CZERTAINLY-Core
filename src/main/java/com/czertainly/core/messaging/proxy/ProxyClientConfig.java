package com.czertainly.core.messaging.proxy;

import com.czertainly.api.clients.mq.HealthApiClient;
import com.czertainly.api.clients.mq.ProxyClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class that enables proxy client functionality.
 * Automatically registers ProxyProperties configuration.
 */
@Configuration
@EnableConfigurationProperties(ProxyProperties.class)
public class ProxyClientConfig {

    /**
     * Create MQ-based HealthApiClient bean.
     * This bean is used by ConnectorServiceImpl when connector has proxyId set.
     */
    @Bean
    public HealthApiClient mqHealthApiClient(ProxyClient proxyClient) {
        return new HealthApiClient(proxyClient);
    }
}
