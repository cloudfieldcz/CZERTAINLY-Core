package com.czertainly.core.messaging.proxy.handler;

import com.czertainly.api.clients.mq.model.ProxyResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for messageType-based response handlers.
 * Dispatches proxy responses to appropriate handlers based on messageType.
 *
 * <p>This enables fire-and-forget style messaging where any instance can process
 * a response based on its messageType, rather than requiring correlation-based
 * routing back to the originating instance.</p>
 *
 * <p>Pattern matching supports:</p>
 * <ul>
 *   <li>Exact match: "certificate.issued" matches exactly</li>
 *   <li>Wildcard suffix: "POST:/v1/certificates/*" matches any messageType starting with "POST:/v1/certificates/"</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnBean(MessageTypeResponseHandler.class)
public class MessageTypeHandlerRegistry {

    private final Map<String, MessageTypeResponseHandler> handlers = new ConcurrentHashMap<>();
    private final List<MessageTypeResponseHandler> registeredHandlers;

    public MessageTypeHandlerRegistry(List<MessageTypeResponseHandler> registeredHandlers) {
        this.registeredHandlers = registeredHandlers != null ? registeredHandlers : List.of();
    }

    @PostConstruct
    public void init() {
        for (MessageTypeResponseHandler handler : registeredHandlers) {
            String messageType = handler.getMessageType();
            if (messageType != null && !messageType.isBlank()) {
                MessageTypeResponseHandler existing = handlers.get(messageType);
                if (existing != null) {
                    log.warn("Duplicate messageType '{}' detected: handler {} would overwrite existing handler {}. Keeping first registered handler.",
                            messageType, handler.getClass().getSimpleName(), existing.getClass().getSimpleName());
                    continue;
                }
                handlers.put(messageType, handler);
                log.info("Registered messageType handler: {} -> {}", messageType, handler.getClass().getSimpleName());
            } else {
                log.warn("Skipping handler with null/blank messageType: {}", handler.getClass().getSimpleName());
            }
        }
        log.info("MessageTypeHandlerRegistry initialized with {} handlers: {}", handlers.size(), handlers.keySet());
    }

    /**
     * Check if a handler exists for the given messageType.
     *
     * @param messageType The message type to check
     * @return true if a handler is registered (exact or pattern match)
     */
    public boolean hasHandler(String messageType) {
        if (messageType == null) {
            return false;
        }
        return handlers.containsKey(messageType) || findPatternMatch(messageType) != null;
    }

    /**
     * Dispatch a response to the appropriate handler based on messageType.
     *
     * @param response The proxy response to dispatch
     * @return true if a handler was found and invoked, false otherwise
     */
    public boolean dispatch(ProxyResponse response) {
        if (response == null) {
            log.debug("Cannot dispatch null response");
            return false;
        }
        String messageType = response.getMessageType();
        if (messageType == null) {
            log.debug("Cannot dispatch response without messageType");
            return false;
        }

        // Try exact match first
        MessageTypeResponseHandler handler = handlers.get(messageType);

        // Try pattern match if no exact match
        if (handler == null) {
            handler = findPatternMatch(messageType);
        }

        if (handler != null) {
            try {
                log.debug("Dispatching to handler {} for messageType={}", handler.getClass().getSimpleName(), messageType);
                handler.handleResponse(response);
                return true;
            } catch (Exception e) {
                log.error("Error handling response messageType={}: {}", messageType, e.getMessage(), e);
                return false;
            }
        }

        log.debug("No handler found for messageType={}", messageType);
        return false;
    }

    /**
     * Find a handler matching a wildcard pattern.
     * Patterns ending with "/*" match any messageType starting with the prefix.
     * Uses most-specific-wins strategy: longest matching prefix takes precedence.
     */
    private MessageTypeResponseHandler findPatternMatch(String messageType) {
        MessageTypeResponseHandler bestMatch = null;
        int bestPrefixLength = -1;

        for (Map.Entry<String, MessageTypeResponseHandler> entry : handlers.entrySet()) {
            String pattern = entry.getKey();
            if (pattern.endsWith("/*")) {
                String prefix = pattern.substring(0, pattern.length() - 2);
                if (messageType.startsWith(prefix) && prefix.length() > bestPrefixLength) {
                    bestMatch = entry.getValue();
                    bestPrefixLength = prefix.length();
                }
            }
        }
        return bestMatch;
    }

    /**
     * Get the number of registered handlers.
     * Useful for monitoring.
     */
    public int getHandlerCount() {
        return handlers.size();
    }
}
