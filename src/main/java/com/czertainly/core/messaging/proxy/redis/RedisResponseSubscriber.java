package com.czertainly.core.messaging.proxy.redis;

import com.czertainly.api.clients.mq.model.ProxyResponse;
import com.czertainly.core.messaging.proxy.ProxyResponseCorrelator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Subscribes to Redis pub/sub channel and completes local pending requests.
 *
 * <p>When a proxy response is distributed via Redis from another instance,
 * this subscriber checks if the correlation ID matches a local pending request.
 * If found, it completes the pending request's CompletableFuture.</p>
 */
@Slf4j
@Component
@Profile("!test")
@ConditionalOnProperty(prefix = "proxy.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisResponseSubscriber {

    private final ProxyResponseCorrelator correlator;
    private final ObjectMapper objectMapper;

    public RedisResponseSubscriber(ProxyResponseCorrelator correlator, ObjectMapper objectMapper) {
        this.correlator = correlator;
        this.objectMapper = objectMapper;
        log.info("RedisResponseSubscriber initialized");
    }

    /**
     * Handle messages received from Redis pub/sub channel.
     * This method is called by the MessageListenerAdapter.
     *
     * @param message The JSON-serialized ProxyResponse
     */
    public void onMessage(String message) {
        try {
            ProxyResponse response = objectMapper.readValue(message, ProxyResponse.class);
            String correlationId = response.getCorrelationId();

            if (correlationId == null) {
                log.warn("Received proxy response with null correlationId, skipping");
                return;
            }

            log.debug("Received proxy response from Redis: correlationId={}", correlationId);

            // Try to complete local pending request
            boolean completed = correlator.tryCompleteRequest(response);
            if (completed) {
                log.debug("Completed local pending request from Redis distribution: correlationId={}",
                        correlationId);
            }
            // If not completed, this instance doesn't have a pending request for this correlation ID
            // This is expected - the response was distributed to all instances, but only one has the pending request

        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize proxy response from Redis: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error processing proxy response from Redis: {}", e.getMessage(), e);
        }
    }
}
