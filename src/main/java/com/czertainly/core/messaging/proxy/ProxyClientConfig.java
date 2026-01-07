package com.czertainly.core.messaging.proxy;

import com.czertainly.api.clients.mq.AttributeApiClient;
import com.czertainly.api.clients.mq.ConnectorApiClient;
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

    /**
     * Create MQ-based ConnectorApiClient bean.
     * This bean is used when connector has proxyId set to list supported functions.
     */
    @Bean
    public ConnectorApiClient mqConnectorApiClient(ProxyClient proxyClient) {
        return new ConnectorApiClient(proxyClient);
    }

    /**
     * Create MQ-based AttributeApiClient bean.
     * This bean is used when connector has proxyId set to manage attributes.
     */
    @Bean
    public AttributeApiClient mqAttributeApiClient(ProxyClient proxyClient) {
        return new AttributeApiClient(proxyClient);
    }
}
