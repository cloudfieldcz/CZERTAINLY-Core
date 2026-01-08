package com.czertainly.core.client;

import com.czertainly.api.clients.AttributeApiClient;
import com.czertainly.api.interfaces.client.AttributeSyncApiClient;
import com.czertainly.api.clients.ConnectorApiClient;
import com.czertainly.api.interfaces.client.ConnectorSyncApiClient;
import com.czertainly.api.clients.HealthApiClient;
import com.czertainly.api.interfaces.client.HealthSyncApiClient;
import com.czertainly.api.model.core.connector.ConnectorDto;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Factory that returns appropriate API client (REST or MQ) based on connector configuration.
 *
 * <p>This factory centralizes the logic for choosing between REST and MQ-based communication
 * with connectors. When a connector has a proxyId set and the corresponding MQ client is
 * available, the MQ client is returned. Otherwise, the REST client is used.</p>
 *
 * <p>Usage example:</p>
 * <pre>
 * {@code
 * AttributeSyncApiClient client = connectorApiFactory.getAttributeApiClient(connectorDto);
 * client.listAttributeDefinitions(connectorDto, functionGroup, kind);
 * }
 * </pre>
 */
@Component
public class ConnectorApiFactory {

    private static final Logger logger = LoggerFactory.getLogger(ConnectorApiFactory.class);

    // REST clients (always available)
    private final AttributeApiClient restAttributeApiClient;
    private final ConnectorApiClient restConnectorApiClient;
    private final HealthApiClient restHealthApiClient;

    // MQ clients (optional - may be null if proxy/messaging is not enabled)
    private final com.czertainly.api.clients.mq.AttributeApiClient mqAttributeApiClient;
    private final com.czertainly.api.clients.mq.ConnectorApiClient mqConnectorApiClient;
    private final com.czertainly.api.clients.mq.HealthApiClient mqHealthApiClient;

    @Autowired
    public ConnectorApiFactory(
            AttributeApiClient restAttributeApiClient,
            ConnectorApiClient restConnectorApiClient,
            HealthApiClient restHealthApiClient,
            @Autowired(required = false) com.czertainly.api.clients.mq.AttributeApiClient mqAttributeApiClient,
            @Autowired(required = false) com.czertainly.api.clients.mq.ConnectorApiClient mqConnectorApiClient,
            @Autowired(required = false) com.czertainly.api.clients.mq.HealthApiClient mqHealthApiClient) {
        this.restAttributeApiClient = restAttributeApiClient;
        this.restConnectorApiClient = restConnectorApiClient;
        this.restHealthApiClient = restHealthApiClient;
        this.mqAttributeApiClient = mqAttributeApiClient;
        this.mqConnectorApiClient = mqConnectorApiClient;
        this.mqHealthApiClient = mqHealthApiClient;

        logger.info("ConnectorApiFactory initialized. MQ clients available: attribute={}, connector={}, health={}",
                mqAttributeApiClient != null, mqConnectorApiClient != null, mqHealthApiClient != null);
    }

    /**
     * Get attribute API client for the given connector.
     *
     * @param connector Connector configuration
     * @return MQ client if connector has proxyId and MQ client is available, otherwise REST client
     */
    public AttributeSyncApiClient getAttributeApiClient(ConnectorDto connector) {
        Objects.requireNonNull(connector, "connector must not be null");
        if (shouldUseMq(connector) && mqAttributeApiClient != null) {
            logger.debug("Using MQ attribute client for connector {} via proxy {}",
                    connector.getName(), connector.getProxyId());
            return mqAttributeApiClient;
        }
        return restAttributeApiClient;
    }

    /**
     * Get connector API client for the given connector.
     *
     * @param connector Connector configuration
     * @return MQ client if connector has proxyId and MQ client is available, otherwise REST client
     */
    public ConnectorSyncApiClient getConnectorApiClient(ConnectorDto connector) {
        Objects.requireNonNull(connector, "connector must not be null");
        if (shouldUseMq(connector) && mqConnectorApiClient != null) {
            logger.debug("Using MQ connector client for connector {} via proxy {}",
                    connector.getName(), connector.getProxyId());
            return mqConnectorApiClient;
        }
        return restConnectorApiClient;
    }

    /**
     * Get health API client for the given connector.
     *
     * @param connector Connector configuration
     * @return MQ client if connector has proxyId and MQ client is available, otherwise REST client
     */
    public HealthSyncApiClient getHealthApiClient(ConnectorDto connector) {
        Objects.requireNonNull(connector, "connector must not be null");
        if (shouldUseMq(connector) && mqHealthApiClient != null) {
            logger.debug("Using MQ health client for connector {} via proxy {}",
                    connector.getName(), connector.getProxyId());
            return mqHealthApiClient;
        }
        return restHealthApiClient;
    }

    /**
     * Check if MQ-based communication should be used for the given connector.
     *
     * @param connector Connector configuration
     * @return true if connector has a non-empty proxyId set
     */
    private boolean shouldUseMq(ConnectorDto connector) {
        return connector.getProxyId() != null && !connector.getProxyId().isBlank();
    }

    /**
     * Check if MQ clients are available.
     *
     * @return true if at least one MQ client is available
     */
    public boolean isMqEnabled() {
        return mqAttributeApiClient != null || mqConnectorApiClient != null || mqHealthApiClient != null;
    }
}
