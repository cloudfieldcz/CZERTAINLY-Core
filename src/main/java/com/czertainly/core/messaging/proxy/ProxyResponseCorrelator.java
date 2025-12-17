package com.czertainly.core.messaging.proxy;

import com.czertainly.api.clients.mq.model.ProxyResponse;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;

/**
 * Manages correlation between proxy requests and responses.
 * Uses CompletableFuture to allow callers to wait for responses.
 *
 * <p>When a request is sent, a pending request is registered with a correlation ID.
 * When the response arrives, the correlation ID is used to find and complete
 * the appropriate CompletableFuture.</p>
 *
 * <p>Timeout handling is performed by a scheduled executor that cancels
 * pending requests after their timeout expires.</p>
 */
@Slf4j
@Component
public class ProxyResponseCorrelator {

    private final ConcurrentHashMap<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timeoutScheduler;
    private final ProxyProperties proxyProperties;
    private volatile boolean shuttingDown = false;

    /**
     * Internal class representing a pending request awaiting response.
     */
    private record PendingRequest(
            CompletableFuture<ProxyResponse> future,
            Instant createdAt,
            ScheduledFuture<?> timeoutTask
    ) {}

    public ProxyResponseCorrelator(ProxyProperties proxyProperties) {
        this.proxyProperties = proxyProperties;
        // Use virtual threads for the timeout scheduler
        this.timeoutScheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("proxy-timeout-", 0).factory()
        );
        log.info("ProxyResponseCorrelator initialized with timeout scheduler");
    }

    /**
     * Register a pending request for correlation.
     * Must be called BEFORE sending the request to avoid race conditions.
     *
     * @param correlationId Unique ID for this request
     * @param timeout       How long to wait for response
     * @return CompletableFuture that will complete with the response
     * @throws IllegalStateException if too many pending requests
     */
    public CompletableFuture<ProxyResponse> registerRequest(String correlationId, Duration timeout) {
        if (shuttingDown) {
            throw new IllegalStateException("ProxyResponseCorrelator is shutting down");
        }

        // Check capacity
        if (pendingRequests.size() >= proxyProperties.maxPendingRequests()) {
            throw new IllegalStateException(
                    "Too many pending proxy requests. Max: " + proxyProperties.maxPendingRequests());
        }

        CompletableFuture<ProxyResponse> future = new CompletableFuture<>();

        // Schedule timeout
        ScheduledFuture<?> timeoutTask = timeoutScheduler.schedule(
                () -> handleTimeout(correlationId),
                timeout.toMillis(),
                TimeUnit.MILLISECONDS
        );

        PendingRequest pending = new PendingRequest(future, Instant.now(), timeoutTask);
        pendingRequests.put(correlationId, pending);

        log.debug("Registered pending request correlationId={} timeout={}ms pendingCount={}",
                correlationId, timeout.toMillis(), pendingRequests.size());

        return future;
    }

    /**
     * Complete a pending request with a response.
     * Called by the response listener when a response message arrives.
     *
     * @param response The response received from the proxy
     */
    public void completeRequest(ProxyResponse response) {
        String correlationId = response.getCorrelationId();
        if (correlationId == null) {
            log.warn("Received response without correlationId, ignoring");
            return;
        }

        PendingRequest pending = pendingRequests.remove(correlationId);

        if (pending == null) {
            log.warn("Received response for unknown correlationId={}, may have timed out", correlationId);
            return;
        }

        // Cancel the timeout task since we got a response
        pending.timeoutTask().cancel(false);

        // Complete the future with the response
        pending.future().complete(response);

        long latencyMs = Duration.between(pending.createdAt(), Instant.now()).toMillis();
        log.debug("Completed request correlationId={} statusCode={} latency={}ms pendingCount={}",
                correlationId, response.getStatusCode(), latencyMs, pendingRequests.size());
    }

    /**
     * Try to complete a pending request if the correlation ID exists locally.
     * Used by Redis subscriber and JMS listener to check if this instance owns the request.
     *
     * <p>This method is safe to call from any instance - it only completes the request
     * if this instance has a pending request with the matching correlation ID.</p>
     *
     * @param response The response received from the proxy
     * @return true if a pending request was found and completed, false otherwise
     */
    public boolean tryCompleteRequest(ProxyResponse response) {
        String correlationId = response.getCorrelationId();
        if (correlationId == null) {
            log.debug("Cannot try complete request without correlationId");
            return false;
        }

        PendingRequest pending = pendingRequests.remove(correlationId);
        if (pending == null) {
            // No pending request for this correlation ID - this is expected in multi-instance scenarios
            return false;
        }

        // Cancel the timeout task since we got a response
        pending.timeoutTask().cancel(false);

        // Complete the future with the response
        pending.future().complete(response);

        long latencyMs = Duration.between(pending.createdAt(), Instant.now()).toMillis();
        log.debug("Completed request (tryComplete) correlationId={} statusCode={} latency={}ms pendingCount={}",
                correlationId, response.getStatusCode(), latencyMs, pendingRequests.size());
        return true;
    }

    /**
     * Handle timeout for a pending request.
     */
    private void handleTimeout(String correlationId) {
        PendingRequest pending = pendingRequests.remove(correlationId);
        if (pending != null) {
            // Create a timeout response
            ProxyResponse timeoutResponse = ProxyResponse.builder()
                    .correlationId(correlationId)
                    .statusCode(0)
                    .error("Request timed out waiting for proxy response")
                    .errorCategory("timeout")
                    .retryable(true)
                    .timestamp(Instant.now())
                    .build();

            pending.future().complete(timeoutResponse);

            long elapsedMs = Duration.between(pending.createdAt(), Instant.now()).toMillis();
            log.warn("Request timed out correlationId={} elapsed={}ms pendingCount={}",
                    correlationId, elapsedMs, pendingRequests.size());
        }
    }

    /**
     * Cancel a pending request.
     *
     * @param correlationId The correlation ID of the request to cancel
     * @return true if the request was found and cancelled
     */
    public boolean cancelRequest(String correlationId) {
        PendingRequest pending = pendingRequests.remove(correlationId);
        if (pending != null) {
            pending.timeoutTask().cancel(false);
            pending.future().cancel(true);
            log.debug("Cancelled request correlationId={}", correlationId);
            return true;
        }
        return false;
    }

    /**
     * Get the current number of pending requests.
     * Useful for monitoring.
     */
    public int getPendingCount() {
        return pendingRequests.size();
    }

    /**
     * Cleanup on shutdown.
     * Cancels all pending requests and shuts down the timeout scheduler.
     */
    @PreDestroy
    public void shutdown() {
        shuttingDown = true;
        log.info("Shutting down ProxyResponseCorrelator with {} pending requests", pendingRequests.size());

        // Cancel timeout scheduler
        timeoutScheduler.shutdown();
        try {
            if (!timeoutScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                timeoutScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            timeoutScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Complete all pending requests with shutdown error
        pendingRequests.forEach((id, pending) -> {
            pending.timeoutTask().cancel(false);
            ProxyResponse shutdownResponse = ProxyResponse.builder()
                    .correlationId(id)
                    .statusCode(0)
                    .error("ProxyClient shutdown")
                    .errorCategory("connection")
                    .retryable(false)
                    .timestamp(Instant.now())
                    .build();
            pending.future().complete(shutdownResponse);
        });
        pendingRequests.clear();
    }
}
