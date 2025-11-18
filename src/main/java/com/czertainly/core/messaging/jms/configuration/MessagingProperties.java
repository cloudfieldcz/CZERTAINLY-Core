package com.czertainly.core.messaging.jms.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "spring.messaging", ignoreInvalidFields = true, ignoreUnknownFields = true)
@Validated
public record MessagingProperties(
        @NotBlank String brokerUrl,
        @NotBlank String exchange,
        @NotNull @Valid Queue queue,
        @NotBlank String user,
        @NotBlank String password,
        @Valid Listener listener,
        @Valid Producer producer,
        @NotNull @Valid RoutingKey routingKey
) {
    public String destinationActions() {
        return exchange() + "." + queue().actions();
    }

    public String destinationAuditLogs() {
        return exchange() + "." + queue().auditLogs();
    }

    public String destinationEvent() {
        return exchange() + "." + queue().event();
    }

    public String destinationNotifications() {
        return exchange() + "." + queue().notification();
    }

    public String destinationScheduler() {
        return exchange() + "." + queue().scheduler();
    }

    public String destinationValidation() {
        return exchange() + "." + queue().validation();
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
}
