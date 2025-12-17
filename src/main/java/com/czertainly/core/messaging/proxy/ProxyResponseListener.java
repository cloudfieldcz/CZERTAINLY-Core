package com.czertainly.core.messaging.proxy;

import com.czertainly.api.clients.mq.model.ProxyResponse;
import com.czertainly.api.exception.MessageHandlingException;
import com.czertainly.core.messaging.jms.listeners.MessageProcessor;
import com.czertainly.core.messaging.proxy.handler.MessageTypeHandlerRegistry;
import com.czertainly.core.messaging.proxy.redis.RedisResponseDistributor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Listener that processes proxy response messages.
 * Implements MessageProcessor to integrate with the existing JMS listener infrastructure.
 *
 * <p>Response routing decision tree:</p>
 * <ol>
 *   <li>If a handler is registered for the messageType → dispatch to handler (fire-and-forget)</li>
 *   <li>If correlation ID found in local correlator → complete the pending request</li>
 *   <li>Otherwise → distribute via Redis for other instances to handle</li>
 * </ol>
 */
@Slf4j
@Component
public class ProxyResponseListener implements MessageProcessor<ProxyResponse> {

    private final ProxyResponseCorrelator correlator;
    private final MessageTypeHandlerRegistry handlerRegistry;
    private final RedisResponseDistributor redisDistributor;

    public ProxyResponseListener(
            ProxyResponseCorrelator correlator,
            @Autowired(required = false) MessageTypeHandlerRegistry handlerRegistry,
            @Autowired(required = false) RedisResponseDistributor redisDistributor) {
        this.correlator = correlator;
        this.handlerRegistry = handlerRegistry;
        this.redisDistributor = redisDistributor;
        log.info("ProxyResponseListener initialized (handlerRegistry={}, redisDistributor={})",
                handlerRegistry != null ? "enabled" : "disabled",
                redisDistributor != null ? "enabled" : "disabled");
    }

    @Override
    public void processMessage(ProxyResponse response) throws MessageHandlingException {
        if (response == null) {
            log.warn("Received null proxy response, ignoring");
            return;
        }

        String correlationId = response.getCorrelationId();
        String messageType = response.getMessageType();

        log.debug("Processing proxy response correlationId={} messageType={} statusCode={} hasError={}",
                correlationId, messageType, response.getStatusCode(), response.hasError());

        try {
            // 1. Check if handler registered for this messageType (fire-and-forget pattern)
            if (messageType != null && handlerRegistry != null && handlerRegistry.hasHandler(messageType)) {
                log.debug("Dispatching to messageType handler: messageType={}", messageType);
                handlerRegistry.dispatch(response);
                return; // Any instance can handle - done
            }

            // 2. No handler - correlation-based routing
            // Validate correlation ID for correlation-based routing
            if (correlationId == null || correlationId.isBlank()) {
                log.warn("Received proxy response without correlationId and no messageType handler, ignoring. " +
                        "StatusCode: {}, messageType: {}", response.getStatusCode(), messageType);
                return;
            }

            // Try local correlator first
            if (correlator.tryCompleteRequest(response)) {
                log.debug("Completed request from local correlator: correlationId={}", correlationId);
                return; // Handled locally
            }

            // 3. Not found locally - distribute via Redis for other instances
            if (redisDistributor != null) {
                log.debug("Distributing response via Redis: correlationId={}", correlationId);
                redisDistributor.publishResponse(response);
            } else {
                // No Redis - this response may be lost if the owning instance is different
                log.warn("Response not handled locally and Redis distribution not available: correlationId={}",
                        correlationId);
            }

        } catch (Exception e) {
            log.error("Failed to process proxy response correlationId={}: {}",
                    correlationId, e.getMessage(), e);
            throw new MessageHandlingException("Failed to process proxy response: " + e.getMessage(), e);
        }
    }
}
