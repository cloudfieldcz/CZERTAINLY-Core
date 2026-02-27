package com.czertainly.core.messaging.jms.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.connection.SingleConnectionFactory;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;

/**
 * Retry listener for JMS operations that provides logging and connection recovery.
 *
 * <p>When a JMS send fails (e.g., Azure Service Bus force-closes the AMQP connection
 * with {@code amqp:connection:forced}), this listener calls
 * {@link SingleConnectionFactory#resetConnection()} on each retry error to close the
 * broken shared connection, so the next retry lazily creates a fresh one.</p>
 *
 * <p>For ServiceBus producers we use {@link SingleConnectionFactory} (no session caching)
 * to avoid the stale-session race condition in {@code CachingConnectionFactory}
 * (Spring #20995/SPR-16450). For RabbitMQ we use {@code CachingConnectionFactory}
 * which extends {@code SingleConnectionFactory}, so {@code resetConnection()} works
 * for both — the override in {@code CachingConnectionFactory} additionally clears
 * the session cache.</p>
 *
 * <p>Thread-safety: This class is stateless (the {@code SingleConnectionFactory} reference
 * is immutable) and safe to use as a singleton registered in
 * {@link org.springframework.retry.support.RetryTemplate}.</p>
 *
 * @see RetryConfig#jmsRetryTemplate(MessagingProperties, SingleConnectionFactory)
 * @see com.czertainly.core.messaging.jms.listeners.AbstractJmsEndpointConfig
 */
public class JmsRetryListener implements RetryListener {
    private static final Logger logger = LoggerFactory.getLogger(JmsRetryListener.class);
    public static final String ENDPOINT_ID_ATTR = "endpointId";

    private final SingleConnectionFactory connectionFactory;

    public JmsRetryListener(SingleConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
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
        // Reset the shared connection so the next retry gets a fresh one.
        // For SingleConnectionFactory (ServiceBus): closes the broken connection and nulls it.
        // For CachingConnectionFactory (RabbitMQ): additionally clears the session cache.
        // The next JmsTemplate.execute() will lazily create a new connection and session.
        logger.warn("JMS operation failed, resetting producer connection before retry: {}", throwable.getMessage());
        connectionFactory.resetConnection();

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
