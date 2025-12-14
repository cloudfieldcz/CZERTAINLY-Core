package com.czertainly.core.messaging.jms.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;

public class JmsRetryListener implements RetryListener {
    private static final Logger logger = LoggerFactory.getLogger(JmsRetryListener.class);
    public static final String ENDPOINT_ID_ATTR = "endpointId";

    @Override
    public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        if (throwable != null && context.getRetryCount() > 0) {
            String endpointId = getEndpointId(context);
            if (endpointId != null) {
                logger.error("Failed to process message in endpoint '{}' after {} attempts. Last exception: {}",
                        endpointId,
                        context.getRetryCount(),
                        throwable.getMessage(),
                        throwable);
            } else {
                // Fallback for producer - does not have endpointId
                logger.error("Failed to send JMS message after {} attempts. Last exception: {}",
                        context.getRetryCount(),
                        throwable.getMessage(),
                        throwable);
            }
        }
    }

    @Override
    public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        String endpointId = getEndpointId(context);
        if (endpointId != null) {
            logger.warn("Retry attempt {} failed in endpoint '{}': {}",
                    context.getRetryCount(),
                    endpointId,
                    throwable.getMessage());
        } else {
            // Fallback for producer
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