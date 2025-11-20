package com.czertainly.core.messaging.jms.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "spring.messaging", ignoreInvalidFields = true, ignoreUnknownFields = true)
@Validated
public record MessagingProperties(
        @NotNull BrokerName name,
        @NotBlank String brokerUrl,
        @NotBlank String exchange,
        String exchangePrefix,
        @NotBlank String user,
        @NotBlank String password,
        @Valid Listener listener,
        @Valid Producer producer,
        @Valid Queue queue,
        @NotNull @Valid RoutingKey routingKey
) {
    private String producerDestination(String routingKey) {
        if (name == BrokerName.SERVICEBUS) {
            return exchange();
        }

        if (exchangePrefix != null) {
            return exchangePrefix + exchange() + "/" + routingKey;
        }
        return exchange() + "/" + routingKey;
    }

    public String produceDestinationActions() {
        return producerDestination(routingKey().actions());
    }

    public String produceDestinationAuditLogs() {
        return producerDestination(routingKey().auditLogs());
    }

    public String produceDestinationEvent() {
        return producerDestination(routingKey().event());
    }

    public String produceDestinationNotifications() {
        return producerDestination(routingKey().notification());
    }

    public String produceDestinationScheduler() {
        return producerDestination(routingKey().scheduler());
    }

    public String produceDestinationValidation() {
        return producerDestination(routingKey().validation());
    }

    public record Queue (
            @NotBlank String actions,
            @NotBlank String auditLogs,
            @NotBlank String event,
            @NotBlank String notification,
            @NotBlank String scheduler,
            @NotBlank String validation
    ) {}

    public record RoutingKey(
            @NotBlank String actions,
            @NotBlank String auditLogs,
            @NotBlank String event,
            @NotBlank String notification,
            @NotBlank String scheduler,
            @NotBlank String validation
    ) {}

    public record Listener(
            Long recoveryInterval
    ) {}

    public record Producer(
            Retry retry
    ) {}

    public record Retry(
            Boolean enabled,
            Long initialInterval,
            Integer maxAttempts,
            Long maxInterval,
            Long multiplier
    ) {}

    public enum BrokerName {
        RABBITMQ,
        SERVICEBUS
    }
}
