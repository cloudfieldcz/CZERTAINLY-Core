package com.czertainly.core.messaging.jms.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
@EnableRetry
public class RetryConfig {

    @Bean
    public RetryTemplate jmsRetryTemplate(MessagingProperties messagingProperties) {
        RetryTemplate template = new RetryTemplate();

        if (messagingProperties.producer() != null && messagingProperties.producer().retry() != null &&
                messagingProperties.producer().retry().enabled()) {

            SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(messagingProperties.producer().retry().maxAttempts());
            template.setRetryPolicy(retryPolicy);

            // Exponential backoff: 3s → 6s → max 10s
            ExponentialBackOffPolicy backoff = new ExponentialBackOffPolicy();
            backoff.setInitialInterval(messagingProperties.producer().retry().initialInterval());
            backoff.setMultiplier(messagingProperties.producer().retry().multiplier());
            backoff.setMaxInterval(messagingProperties.producer().retry().maxInterval());
            template.setBackOffPolicy(backoff);
        } else {
            RetryPolicy neverRetryPolicy = new NeverRetryPolicy();
            template.setRetryPolicy(neverRetryPolicy);
        }

        return template;
    }

}
