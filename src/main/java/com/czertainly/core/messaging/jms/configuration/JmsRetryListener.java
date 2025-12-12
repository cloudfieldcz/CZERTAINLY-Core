package com.czertainly.core.messaging.jms.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;

public class JmsRetryListener implements RetryListener {
    private static final Logger logger = LoggerFactory.getLogger(JmsRetryListener.class);

    @Override
    public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        if (throwable != null && context.getRetryCount() > 0) {
            logger.error("Failed to send JMS message after {} attempts. Last exception: {}",
                    context.getRetryCount(),
                    throwable.getMessage(),
                    throwable);
        }
    }

    @Override
    public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        logger.warn("Retry attempt {} failed for JMS message: {}",
                context.getRetryCount(),
                throwable.getMessage());
    }
}