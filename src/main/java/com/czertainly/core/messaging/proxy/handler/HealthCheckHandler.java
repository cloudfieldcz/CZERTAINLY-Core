package com.czertainly.core.messaging.proxy.handler;

import com.czertainly.api.clients.mq.model.ProxyMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handler for health check messages from proxy instances.
 *
 * <p>Health check messages are fire-and-forget style messages that indicate
 * a proxy instance is alive and connected. This handler logs the health check
 * for monitoring purposes.</p>
 */
@Slf4j
@Component
public class HealthCheckHandler implements MessageTypeResponseHandler {

    private static final String MESSAGE_TYPE = "health.*";

    @Override
    public String getMessageType() {
        return MESSAGE_TYPE;
    }

    @Override
    public void handleResponse(ProxyMessage message) {
        log.info("Health check received from proxy: proxyId={} timestamp={}",
                message.getProxyId(), message.getTimestamp());
    }
}
