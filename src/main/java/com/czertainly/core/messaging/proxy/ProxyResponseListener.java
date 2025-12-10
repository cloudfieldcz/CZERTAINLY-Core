package com.czertainly.core.messaging.proxy;

import com.czertainly.api.clients.mq.model.ProxyResponse;
import com.czertainly.api.exception.MessageHandlingException;
import com.czertainly.core.messaging.jms.listeners.MessageProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Listener that processes proxy response messages.
 * Implements MessageProcessor to integrate with the existing JMS listener infrastructure.
 *
 * <p>When a proxy response message arrives, this listener extracts the correlation ID
 * and passes the response to the correlator to complete the pending request.</p>
 */
@Slf4j
@Component
public class ProxyResponseListener implements MessageProcessor<ProxyResponse> {

    private final ProxyResponseCorrelator correlator;

    public ProxyResponseListener(ProxyResponseCorrelator correlator) {
        this.correlator = correlator;
        log.info("ProxyResponseListener initialized");
    }

    @Override
    public void processMessage(ProxyResponse response) throws MessageHandlingException {
        if (response == null) {
            log.warn("Received null proxy response, ignoring");
            return;
        }

        String correlationId = response.getCorrelationId();
        if (correlationId == null || correlationId.isBlank()) {
            log.warn("Received proxy response without correlationId, ignoring. StatusCode: {}",
                    response.getStatusCode());
            return;
        }

        log.debug("Processing proxy response correlationId={} statusCode={} hasError={}",
                correlationId, response.getStatusCode(), response.hasError());

        try {
            correlator.completeRequest(response);
        } catch (Exception e) {
            log.error("Failed to process proxy response correlationId={}: {}",
                    correlationId, e.getMessage(), e);
            throw new MessageHandlingException("Failed to process proxy response: " + e.getMessage());
        }
    }
}
