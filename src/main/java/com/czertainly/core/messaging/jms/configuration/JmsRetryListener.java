package com.czertainly.core.messaging.jms.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;

/**
 * Retry listener for JMS operations that provides logging and connection cache recovery.
 *
 * <p>When a JMS send fails due to a stale cached producer (e.g., Azure Service Bus
 * force-detaches an AMQP link after idle timeout), Spring's {@link CachingConnectionFactory}
 * may not automatically invalidate the cached session/producer because the underlying
 * connection is still alive. This listener calls {@link CachingConnectionFactory#resetConnection()}
 * on each retry error to force the cache to be cleared, so the next retry gets a fresh
 * session and producer.</p>
 *
 * <p>Thread-safety: This class is stateless (the {@code CachingConnectionFactory} reference
 * is immutable) and safe to use as a singleton registered in
 * {@link org.springframework.retry.support.RetryTemplate}.</p>
 *
 * @see RetryConfig#jmsRetryTemplate(MessagingProperties, CachingConnectionFactory)
 * @see com.czertainly.core.messaging.jms.listeners.AbstractJmsEndpointConfig
 */
public class JmsRetryListener implements RetryListener {
    private static final Logger logger = LoggerFactory.getLogger(JmsRetryListener.class);
    public static final String ENDPOINT_ID_ATTR = "endpointId";

    private final CachingConnectionFactory cachingConnectionFactory;

    public JmsRetryListener(CachingConnectionFactory cachingConnectionFactory) {
        this.cachingConnectionFactory = cachingConnectionFactory;
    }

    @Override
    public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
        String endpointId = getEndpointId(context);
        if (endpointId != null) {
            logger.debug("Starting retry operation for endpoint '{}'", endpointId);
        }
        return true;
    }

    @Override
    public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        if (throwable != null) {
            String endpointId = getEndpointId(context);
            if (endpointId != null) {
                logger.error("Failed to process message in endpoint '{}' (messageId={}, type={}) after {} attempts",
                        endpointId,
                        context.getAttribute("messageId"),
                        context.getAttribute("messageClass"),
                        context.getRetryCount(),
                        throwable);
            } else {
                logger.error("Failed to process message for (messageId={}, type={}) after {} attempts",
                        context.getAttribute("messageId"),
                        context.getAttribute("messageClass"),
                        context.getRetryCount(),
                        throwable);
            }
        }
    }

    @Override
    public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        // Reset the connection cache to discard stale sessions/producers.
        // This is critical for Azure Service Bus where individual AMQP links can be
        // force-detached (idle timeout) while the connection itself remains open.
        // Without this reset, CachingConnectionFactory keeps serving the stale cached
        // producer on every retry, causing all attempts to fail with the same error.
        logger.warn("JMS operation failed, resetting producer connection cache before retry: {}", throwable.getMessage());
        cachingConnectionFactory.resetConnection();

        String endpointId = getEndpointId(context);
        if (endpointId != null) {
            logger.warn("Retry attempt {} failed in endpoint '{}': {}",
                    context.getRetryCount(),
                    endpointId,
                    throwable.getMessage(),
                    throwable);
        } else {
            logger.warn("Retry attempt {} failed for JMS message: {}",
                    context.getRetryCount(),
                    throwable.getMessage());
        }
    }

    private String getEndpointId(RetryContext context) {
        Object endpointId = context.getAttribute(ENDPOINT_ID_ATTR);
        return endpointId != null ? endpointId.toString() : null;
    }
}
