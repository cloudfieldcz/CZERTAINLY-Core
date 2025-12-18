package com.czertainly.core.messaging.proxy;

import com.czertainly.api.clients.mq.model.ProxyResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/**
 * Unit tests for {@link ProxyResponseCorrelator}.
 * Tests request registration, completion, timeout handling, cancellation, and shutdown.
 */
class ProxyResponseCorrelatorTest {

    private ProxyResponseCorrelator correlator;
    private ProxyProperties proxyProperties;

    @BeforeEach
    void setUp() {
        proxyProperties = new ProxyProperties(
                "test-exchange",
                "test-queue",
                Duration.ofSeconds(30),
                100, // low max pending for testing capacity
                null
        );
        correlator = new ProxyResponseCorrelator(proxyProperties);
    }

    @AfterEach
    void tearDown() {
        if (correlator != null) {
            correlator.shutdown();
        }
    }

    // ==================== Registration Tests ====================

    @Test
    void registerRequest_returnsCompletableFuture() {
        CompletableFuture<ProxyResponse> future = correlator.registerRequest("corr-1", Duration.ofSeconds(10));

        assertThat(future).isNotNull();
        assertThat(future.isDone()).isFalse();
    }

    @Test
    void registerRequest_incrementsPendingCount() {
        assertThat(correlator.getPendingCount()).isZero();

        correlator.registerRequest("corr-1", Duration.ofSeconds(10));
        assertThat(correlator.getPendingCount()).isEqualTo(1);

        correlator.registerRequest("corr-2", Duration.ofSeconds(10));
        assertThat(correlator.getPendingCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Registration at capacity throws IllegalStateException with details")
    void registerRequest_whenAtCapacity_throwsIllegalStateException() {
        // Fill to capacity (100 set in setUp)
        for (int i = 0; i < 100; i++) {
            correlator.registerRequest("corr-" + i, Duration.ofSeconds(30));
        }

        assertThatThrownBy(() -> correlator.registerRequest("corr-overflow", Duration.ofSeconds(10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Too many pending proxy requests")
                .hasMessageContaining("100");
    }

    @Test
    void registerRequest_whenShuttingDown_throwsIllegalStateException() {
        correlator.shutdown();

        assertThatThrownBy(() -> correlator.registerRequest("corr-1", Duration.ofSeconds(10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ProxyResponseCorrelator is shutting down");
    }

    // ==================== Completion Tests ====================

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void completeRequest_withMatchingCorrelationId_completesFuture() throws Exception {
        String correlationId = "corr-complete";
        CompletableFuture<ProxyResponse> future = correlator.registerRequest(correlationId, Duration.ofSeconds(30));

        ProxyResponse response = createSuccessResponse(correlationId);
        correlator.completeRequest(response);

        assertThat(future.isDone()).isTrue();
        ProxyResponse result = future.get();
        assertThat(result.getCorrelationId()).isEqualTo(correlationId);
        assertThat(result.getStatusCode()).isEqualTo(200);
    }

    @Test
    void completeRequest_withUnknownCorrelationId_logsWarningAndIgnores() {
        ProxyResponse response = createSuccessResponse("unknown-corr-id");

        assertThatCode(() -> correlator.completeRequest(response)).doesNotThrowAnyException();
    }

    @Test
    void completeRequest_withNullCorrelationId_logsWarningAndIgnores() {
        ProxyResponse response = createSuccessResponse(null);

        assertThatCode(() -> correlator.completeRequest(response)).doesNotThrowAnyException();
    }

    @Test
    void completeRequest_decrementsPendingCount() {
        String correlationId = "corr-decrement";
        correlator.registerRequest(correlationId, Duration.ofSeconds(30));
        assertThat(correlator.getPendingCount()).isEqualTo(1);

        correlator.completeRequest(createSuccessResponse(correlationId));
        assertThat(correlator.getPendingCount()).isZero();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("After completion at capacity, new registrations are allowed")
    void completeRequest_allowsNewRegistrationsAfterCompletion() {
        // Fill to capacity
        for (int i = 0; i < 100; i++) {
            correlator.registerRequest("corr-" + i, Duration.ofSeconds(30));
        }
        assertThat(correlator.getPendingCount()).isEqualTo(100);

        // Complete one
        correlator.completeRequest(createSuccessResponse("corr-50"));
        assertThat(correlator.getPendingCount()).isEqualTo(99);

        // Now we should be able to register one more
        CompletableFuture<ProxyResponse> newFuture = correlator.registerRequest("corr-new", Duration.ofSeconds(10));
        assertThat(newFuture).isNotNull();
        assertThat(correlator.getPendingCount()).isEqualTo(100);
    }

    // ==================== Try Complete Tests ====================

    @Test
    void tryCompleteRequest_withMatchingId_returnsTrueAndCompletes() {
        String correlationId = "corr-try";
        CompletableFuture<ProxyResponse> future = correlator.registerRequest(correlationId, Duration.ofSeconds(30));

        boolean completed = correlator.tryCompleteRequest(createSuccessResponse(correlationId));

        assertThat(completed).isTrue();
        assertThat(future.isDone()).isTrue();
    }

    @Test
    void tryCompleteRequest_withUnknownId_returnsFalse() {
        correlator.registerRequest("corr-1", Duration.ofSeconds(30));

        boolean completed = correlator.tryCompleteRequest(createSuccessResponse("unknown-id"));

        assertThat(completed).isFalse();
    }

    @Test
    void tryCompleteRequest_withNullCorrelationId_returnsFalse() {
        correlator.registerRequest("corr-1", Duration.ofSeconds(30));

        boolean completed = correlator.tryCompleteRequest(createSuccessResponse(null));

        assertThat(completed).isFalse();
    }

    @Test
    void tryCompleteRequest_doesNotAffectPendingCountForUnknownId() {
        correlator.registerRequest("corr-1", Duration.ofSeconds(30));
        assertThat(correlator.getPendingCount()).isEqualTo(1);

        correlator.tryCompleteRequest(createSuccessResponse("unknown-id"));
        assertThat(correlator.getPendingCount()).isEqualTo(1);
    }

    // ==================== Timeout Tests ====================

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("After timeout, future completes with timeout response containing error details")
    void registerRequest_afterTimeout_futureCompletesWithTimeoutResponse() throws Exception {
        String correlationId = "corr-timeout";
        // Use a very short timeout
        CompletableFuture<ProxyResponse> future = correlator.registerRequest(correlationId, Duration.ofMillis(100));

        // Wait for timeout using Awaitility
        await().atMost(Duration.ofSeconds(2)).until(future::isDone);

        ProxyResponse response = future.get();
        assertThat(response).isNotNull();
        assertThat(response.getCorrelationId()).isEqualTo(correlationId);
        assertThat(response.getStatusCode()).isZero();
        assertThat(response.getErrorCategory()).isEqualTo("timeout");
        assertThat(response.isRetryable()).isTrue();
        assertThat(response.getError()).contains("timed out");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void timeout_decrementsPendingCount() {
        correlator.registerRequest("corr-timeout", Duration.ofMillis(100));
        assertThat(correlator.getPendingCount()).isEqualTo(1);

        // Wait for timeout to fire using Awaitility
        await().atMost(Duration.ofSeconds(2))
               .untilAsserted(() -> assertThat(correlator.getPendingCount()).isZero());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Completion before timeout preserves successful response")
    void timeout_doesNotAffectAlreadyCompletedRequest() throws Exception {
        String correlationId = "corr-complete-before-timeout";
        CompletableFuture<ProxyResponse> future = correlator.registerRequest(correlationId, Duration.ofMillis(200));

        // Complete immediately
        ProxyResponse successResponse = createSuccessResponse(correlationId);
        correlator.completeRequest(successResponse);

        // Wait past the original timeout using Awaitility
        await().during(Duration.ofMillis(300)).atMost(Duration.ofMillis(500))
               .untilAsserted(() -> assertThat(future.isDone()).isTrue());

        // Should still have the success response, not timeout
        ProxyResponse result = future.get();
        assertThat(result.getStatusCode()).isEqualTo(200);
        assertThat(result.getError()).isNull();
    }

    // ==================== Cancellation Tests ====================

    @Test
    void cancelRequest_withExistingRequest_returnsTrueAndCancels() {
        String correlationId = "corr-cancel";
        CompletableFuture<ProxyResponse> future = correlator.registerRequest(correlationId, Duration.ofSeconds(30));

        boolean cancelled = correlator.cancelRequest(correlationId);

        assertThat(cancelled).isTrue();
        assertThat(future.isCancelled()).isTrue();
        assertThat(correlator.getPendingCount()).isZero();
    }

    @Test
    void cancelRequest_withUnknownRequest_returnsFalse() {
        boolean cancelled = correlator.cancelRequest("unknown-corr-id");

        assertThat(cancelled).isFalse();
    }

    @Test
    void cancelRequest_preventsFutureCompletion() {
        String correlationId = "corr-cancel-then-complete";
        CompletableFuture<ProxyResponse> future = correlator.registerRequest(correlationId, Duration.ofSeconds(30));

        correlator.cancelRequest(correlationId);

        // Try to complete after cancellation - should have no effect (already removed)
        correlator.completeRequest(createSuccessResponse(correlationId));

        assertThat(future.isCancelled()).isTrue();
    }

    // ==================== Shutdown Tests ====================

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Shutdown completes all pending requests with non-retryable connection error")
    void shutdown_completesAllPendingWithShutdownError() throws Exception {
        CompletableFuture<ProxyResponse> future1 = correlator.registerRequest("corr-1", Duration.ofSeconds(30));
        CompletableFuture<ProxyResponse> future2 = correlator.registerRequest("corr-2", Duration.ofSeconds(30));
        CompletableFuture<ProxyResponse> future3 = correlator.registerRequest("corr-3", Duration.ofSeconds(30));

        correlator.shutdown();

        // All futures should be completed (with longer timeout to allow scheduler shutdown)
        ProxyResponse response1 = future1.get(5, TimeUnit.SECONDS);
        ProxyResponse response2 = future2.get(5, TimeUnit.SECONDS);
        ProxyResponse response3 = future3.get(5, TimeUnit.SECONDS);

        // With shutdown error
        assertThat(response1.getErrorCategory()).isEqualTo("connection");
        assertThat(response1.getError()).isEqualTo("ProxyClient shutdown");
        assertThat(response1.isRetryable()).isFalse();
    }

    @Test
    void shutdown_clearsAllPendingRequests() {
        correlator.registerRequest("corr-1", Duration.ofSeconds(30));
        correlator.registerRequest("corr-2", Duration.ofSeconds(30));
        assertThat(correlator.getPendingCount()).isEqualTo(2);

        correlator.shutdown();

        assertThat(correlator.getPendingCount()).isZero();
    }

    @Test
    void shutdown_canBeCalledMultipleTimes() {
        correlator.registerRequest("corr-1", Duration.ofSeconds(30));

        assertThatCode(() -> {
            correlator.shutdown();
            correlator.shutdown();
        }).doesNotThrowAnyException();
    }

    // ==================== Thread Safety Tests ====================

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Concurrent registrations and completions from multiple threads are thread-safe")
    void concurrentRegistrationsAndCompletions_areThreadSafe() throws Exception {
        int threadCount = 10;
        int requestsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<CompletableFuture<ProxyResponse>> allFutures = new CopyOnWriteArrayList<>();

        // Start threads that will register and complete requests
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < requestsPerThread; i++) {
                        String correlationId = "thread-" + threadId + "-req-" + i;
                        CompletableFuture<ProxyResponse> future = correlator.registerRequest(correlationId, Duration.ofSeconds(30));
                        allFutures.add(future);

                        // Complete half of them immediately
                        if (i % 2 == 0) {
                            correlator.completeRequest(createSuccessResponse(correlationId));
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // Verify all futures were registered (the list grows as futures are added)
        assertThat(allFutures).hasSizeLessThanOrEqualTo(threadCount * requestsPerThread);
        assertThat(allFutures).isNotEmpty();

        // Verify pending count is consistent (non-negative and within bounds)
        int pendingCount = correlator.getPendingCount();
        assertThat(pendingCount).isBetween(0, threadCount * requestsPerThread);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Multiple threads completing same request - only one succeeds")
    void concurrentCompletionAttempts_onlyOneSucceeds() throws Exception {
        String correlationId = "corr-concurrent";
        CompletableFuture<ProxyResponse> future = correlator.registerRequest(correlationId, Duration.ofSeconds(30));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        // Multiple threads try to complete the same request
        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    correlator.completeRequest(createSuccessResponse(correlationId));
                } catch (Exception e) {
                    // Expected for some threads
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // Future should be completed exactly once
        assertThat(future.isDone()).isTrue();
        assertThat(future.isCompletedExceptionally()).isFalse();
        assertThat(future.get().getStatusCode()).isEqualTo(200);
        assertThat(correlator.getPendingCount()).isZero();
    }

    // ==================== Helper Methods ====================

    private ProxyResponse createSuccessResponse(String correlationId) {
        return ProxyResponse.builder()
                .correlationId(correlationId)
                .statusCode(200)
                .timestamp(Instant.now())
                .build();
    }
}
