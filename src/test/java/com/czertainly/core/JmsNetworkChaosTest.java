package com.czertainly.core;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.core.messaging.jms.configuration.MessagingProperties;
import com.czertainly.core.messaging.model.EventMessage;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.JmsException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Chaos engineering tests for JMS messaging resilience.
 * Uses Toxiproxy to simulate realistic network failure scenarios including:
 * - Connection outages (short and long)
 * - Network latency
 * - Connection timeouts
 * - Bandwidth throttling (slicer)
 *
 * These tests verify that the retry mechanism handles transient failures correctly
 * and fails gracefully when failures exceed retry limits.
 *
 * Retry configuration is read from application.yml and all timing expectations
 * are calculated dynamically based on this configuration.
 *
 * Expected execution time: ~5-10 seconds for all tests combined (with current config: 3 retries, 200ms initial interval).
 *
 * @see JmsResilienceTests for base configuration
 * @see com.czertainly.core.messaging.jms.configuration.RetryConfig
 */
public class JmsNetworkChaosTest extends JmsResilienceTests {

    @Autowired
    private MessagingProperties messagingProperties;

    private RetryTimingCalculator retryCalculator;

    @BeforeEach
    void setupRetryCalculator() {
        if (retryCalculator == null) {  // 👈 Initialize only once
            MessagingProperties.Retry retryConfig = messagingProperties.producer().retry();
            retryCalculator = new RetryTimingCalculator(
                    retryConfig.initialInterval(),
                    retryConfig.maxInterval(),
                    retryConfig.multiplier(),
                    retryConfig.maxAttempts()
            );

            // Logs only on FIRST initialization
            logger.info("Retry configuration: initialInterval={}ms, maxInterval={}ms, multiplier={}, maxAttempts={}",
                    retryConfig.initialInterval(), retryConfig.maxInterval(),
                    retryConfig.multiplier(), retryConfig.maxAttempts());
            logger.info("Calculated retry delays: {}", retryCalculator.getAllRetryDelays());
            logger.info("Total retry duration: {}ms", retryCalculator.totalRetryDuration());
        }
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testConnectionIsUp() {
        eventProducer.sendMessage(new EventMessage(ResourceEvent.CERTIFICATE_DISCOVERED, Resource.DISCOVERY, UUID.randomUUID(), "testData"));
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testConnectionDownForShortTime() throws Exception {
        // Restore connection before all retries are exhausted
        // We'll restore after 2nd retry: delays[0] + delays[1] = 200ms + 400ms = 600ms
        long outageMs = retryCalculator.cumulativeWaitBeforeAttempt(2);
        float outageSeconds = ((float) outageMs / 1000);

        // Expected minimum duration is the outage time (connection restored during retries)
        long expectedMinDurationMs = outageMs - 500; // Tolerance for timing variance

        logger.info("Test plan: Outage for {}s, restoring before attempt 3 completes", outageSeconds);

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        try {
            // 1. Simulate connection outage
            proxy.disable();
            logger.info("=== Connection DOWN for {} seconds ===", outageSeconds);

            // 2. Schedule connection restoration BEFORE all retries are exhausted
            var restoreFuture = scheduler.schedule(() -> {
                try {
                    proxy.enable();
                    logger.info("=== Connection RESTORED ===");
                } catch (Exception e) {
                    logger.error("Failed to restore connection", e);
                    throw new RuntimeException(e);
                }
            }, outageMs, TimeUnit.MILLISECONDS);

            // 3. Send message - should succeed after retry (when connection is restored)
            long startTime = System.currentTimeMillis();

            Assertions.assertDoesNotThrow(() -> {
                eventProducer.sendMessage(new EventMessage(
                        ResourceEvent.CERTIFICATE_DISCOVERED,
                        Resource.DISCOVERY,
                        UUID.randomUUID(),
                        "testData"
                ));
            }, "RetryTemplate should handle temporary outage");

            long duration = System.currentTimeMillis() - startTime;

            // 4. Wait for restoration to complete to avoid race condition
            restoreFuture.get(5, TimeUnit.SECONDS);

            // 5. Verify that retry actually happened
            Assertions.assertTrue(duration >= expectedMinDurationMs,
                    String.format("Message should be sent after retry attempts (took %dms, expected >= %dms)",
                            duration, expectedMinDurationMs));

            logger.info("=== Message sent successfully after {} ms (retry attempts) ===", duration);

        } finally {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("Scheduler did not terminate in time");
            }
        }
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testConnectionDownForLongTime() throws Exception {
        // Connection stays down to exhaust all retry attempts
        long totalRetryDuration = retryCalculator.totalRetryDuration();
        // Conservative: allow 50% tolerance for timing variance (JMS overhead, network delays, etc.)
        long expectedMinDuration = totalRetryDuration / 2;

        logger.info("Test plan: Connection down to exhaust all {} retry attempts (total {}ms)",
                retryCalculator.getMaxAttempts(), totalRetryDuration);
        logger.info("Retry delays: {}", retryCalculator.getAllRetryDelays());

        try {
            // 1. Simulate long connection outage
            proxy.disable();
            logger.info("=== Connection DOWN (long outage exceeding retry limit) ===");

            // 2. Send message - should fail after all retry attempts are exhausted
            long startTime = System.currentTimeMillis();

            Assertions.assertThrows(JmsException.class, () -> {
                eventProducer.sendMessage(new EventMessage(
                        ResourceEvent.CERTIFICATE_DISCOVERED,
                        Resource.DISCOVERY,
                        UUID.randomUUID(),
                        "testData"
                ));
            }, "Message should fail after all retry attempts exhausted due to prolonged outage");

            long duration = System.currentTimeMillis() - startTime;

            // 3. Verify that all retry attempts happened
            Assertions.assertTrue(duration >= expectedMinDuration,
                    String.format("Should have attempted all retries (took %dms, expected >= %dms)",
                            duration, expectedMinDuration));

            logger.info("=== Message failed after {} ms ({} retry attempts exhausted) ===",
                    duration, retryCalculator.getMaxAttempts());

            // 4. Restore connection
            proxy.enable();
            logger.info("=== Connection RESTORED ===");

            // 5. Verify that subsequent message succeeds after restoration
            Assertions.assertDoesNotThrow(() -> {
                eventProducer.sendMessage(new EventMessage(
                        ResourceEvent.CERTIFICATE_DISCOVERED,
                        Resource.DISCOVERY,
                        UUID.randomUUID(),
                        "testData"
                ));
            }, "Message should succeed after connection restoration");

            logger.info("=== Subsequent message sent successfully after connection restoration ===");
        } finally {
            // Restore connection (ensure cleanup even on test failure)
            if (proxy != null && !proxy.isEnabled()) {
                proxy.enable();
            }
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testConnectionLatencyShort() throws IOException {
        int latencyMs = 500;  // Reduced from 2000ms to 500ms for faster test execution
        int jitterMs = 100;   // Reduced from 500ms to 100ms
        String toxicName = "latency-toxic";

        // Add latency toxic (500ms ± 100ms)
        proxy.toxics().latency(toxicName, ToxicDirection.UPSTREAM, latencyMs).setJitter(jitterMs);

        logger.info("=== Added latency: {}ms ± {}ms ===", latencyMs, jitterMs);

        // Send message - should succeed despite latency, but take longer
        long startTime = System.currentTimeMillis();

        Assertions.assertDoesNotThrow(() -> {
            eventProducer.sendMessage(new EventMessage(
                    ResourceEvent.CERTIFICATE_DISCOVERED,
                    Resource.DISCOVERY,
                    UUID.randomUUID(),
                    "testData"
            ));
        }, "Message should be sent successfully despite latency");

        long duration = System.currentTimeMillis() - startTime;

        // Verify that latency affected the send time
        // Expected: latency causes delay, but message succeeds (possibly after retries)
        logger.info("=== Message sent in {} ms (with {}ms latency) ===", duration, latencyMs);

        // Verify that latency actually affected the send time (minimum check only)
        // We don't check maximum because latency can trigger retries, and that's expected behavior
        int minExpectedDuration = latencyMs - jitterMs - 200; // ~200ms minimum
        Assertions.assertTrue(duration >= minExpectedDuration,
                String.format("Duration too short (took %dms, expected >= %dms) - latency may not be working",
                        duration, minExpectedDuration));

        logger.info("=== Latency test passed: message handled gracefully {} ===",
                duration > (latencyMs + retryCalculator.getBackoffDelay(0))
                        ? "(with retries)" : "(without retries)");
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testConnectionTimeoutShort() throws Exception {
        int timeoutMs = 500; // Short timeout per attempt
        String toxicName = "timeout-cut";

        // Calculate expected duration: each retry attempt times out after timeoutMs,
        // plus the backoff delays between attempts
        long totalRetryDuration = retryCalculator.totalRetryDuration();
        long expectedMinDuration = (timeoutMs * retryCalculator.getMaxAttempts()) + (totalRetryDuration / 2);

        logger.info("Test plan: {} attempts will timeout after {}ms each, total expected duration >= {}ms",
                retryCalculator.getMaxAttempts(), timeoutMs, expectedMinDuration);

        // Add timeout toxic - each connection attempt will timeout after 500ms
        proxy.toxics().timeout(toxicName, ToxicDirection.UPSTREAM, timeoutMs);
        logger.info("=== Connection timeout {} ms ===", timeoutMs);

        // First message should fail after all retry attempts are exhausted
        long startTime = System.currentTimeMillis();

        Assertions.assertThrows(Exception.class, () -> {
            eventProducer.sendMessage(new EventMessage(
                    ResourceEvent.CERTIFICATE_DISCOVERED,
                    Resource.DISCOVERY,
                    UUID.randomUUID(),
                    "testData"
            ));
        }, "Message should fail after all retry attempts exhausted");

        long duration = System.currentTimeMillis() - startTime;

        // Verify that multiple retry attempts happened
        Assertions.assertTrue(duration >= expectedMinDuration,
                String.format("Should have retried multiple times (took %dms, expected >= %dms)",
                        duration, expectedMinDuration));

        logger.info("=== Message failed after {} ms ({} retry attempts exhausted) ===",
                duration, retryCalculator.getMaxAttempts());

        // Remove toxic to restore connection
        proxy.toxics().get(toxicName).remove();
        logger.info("=== Connection RESTORED (Toxic removed) ===");

        // Second message should succeed after restoration
        Assertions.assertDoesNotThrow(() -> {
            eventProducer.sendMessage(new EventMessage(
                    ResourceEvent.CERTIFICATE_DISCOVERED,
                    Resource.DISCOVERY,
                    UUID.randomUUID(),
                    "testData"
            ));
        }, "Message should succeed after toxic removed");

        logger.info("=== Message sent successfully after toxic removal ===");
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testConnectionSlicer() throws Exception {
        String toxicName = "slicer-toxic";

        int averageSliceSize = 1024;
        int sizeVariation = 256;
        int delayMicros = 20 * 1000;

        // Slicer toxic splits data into small chunks with delay
        proxy.toxics().slicer(toxicName, ToxicDirection.UPSTREAM, averageSliceSize, delayMicros).setSizeVariation(sizeVariation);

        logger.info("=== Added slicer: {}±{} bytes, {}μs delay ===", averageSliceSize, sizeVariation, delayMicros);

        // Generate message ~64 KB
        // (64 * 1024 bytes) / 1024 averageSlice = about 64 chunks
        // Total time: 64 chunks * 20ms = ~1280ms (1.3 seconds)
        String largePayload = generateLargeRandomString(64 * 1024);


        // Send message - should succeed despite slow/chunked transfer
        long startTime = System.currentTimeMillis();

        Assertions.assertDoesNotThrow(() -> {
            eventProducer.sendMessage(new EventMessage(
                    ResourceEvent.CERTIFICATE_DISCOVERED,
                    Resource.DISCOVERY,
                    UUID.randomUUID(),
                    largePayload
            ));
        }, "Message should be sent successfully despite sliced connection");

        long duration = System.currentTimeMillis() - startTime;

        // Verify that slicing actually caused delay
        // Expected: ~1280ms (64 chunks * 20ms), but be conservative due to:
        // - AMQP framing overhead (message will be larger than 64KB)
        // - Serialization overhead (EventMessage wrapping)
        // - Network variability
        int expectedMinDuration = 800; // Conservative: at least 800ms
        int expectedMaxDuration = 3000; // Sanity check: not more than 3 seconds

        Assertions.assertTrue(duration >= expectedMinDuration,
                "Slicing should cause significant delay (took " + duration + "ms, expected >=" + expectedMinDuration + "ms)");

        Assertions.assertTrue(duration <= expectedMaxDuration,
                "Send took too long (took " + duration + "ms, expected <=" + expectedMaxDuration + "ms) - something might be wrong");

        logger.info("=== Message sent in {} ms (with slicing, expected ~1280ms) ===", duration);
    }

    private String generateLargeRandomString(int targetSizeInBytes) {
        int leftLimit = 48; // numeral '0'
        int rightLimit = 122; // letter 'z'
        Random random = new Random();

        return random.ints(leftLimit, rightLimit + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(targetSizeInBytes)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    /**
     * Helper class to calculate retry timing expectations based on configuration.
     * Reads retry configuration from application.yml and computes expected delays
     * using exponential backoff formula: delay[n] = min(initialInterval * multiplier^n, maxInterval)
     */
    private static class RetryTimingCalculator {
        private final long initialInterval;
        private final long maxInterval;
        private final long multiplier;
        private final int maxAttempts;

        RetryTimingCalculator(long initialInterval, long maxInterval, long multiplier, int maxAttempts) {
            this.initialInterval = initialInterval;
            this.maxInterval = maxInterval;
            this.multiplier = multiplier;
            this.maxAttempts = maxAttempts;
        }

        /**
         * Calculate cumulative wait time before Nth retry attempt.
         * @param attemptNumber 1-based attempt number (1 = first retry)
         * @return cumulative wait time in milliseconds
         */
        long cumulativeWaitBeforeAttempt(int attemptNumber) {
            long total = 0;
            for (int i = 0; i < attemptNumber; i++) {
                total += getBackoffDelay(i);
            }
            return total;
        }

        /**
         * Calculate total time until all retries are exhausted.
         * This is the sum of all backoff delays BETWEEN attempts, not including
         * any wait after the final attempt (which never happens).

         * Example with current config (maxAttempts=3):
         * - Attempt 1 fails -> wait delay[0] = 200ms
         * - Attempt 2 fails -> wait delay[1] = 400ms
         * - Attempt 3 fails -> exception thrown (no more waiting)

         * Total = delay[0] + delay[1] = 200 + 400 = 600ms
         *
         * @return total duration in milliseconds
         */
        long totalRetryDuration() {
            // Sum delays for (maxAttempts - 1) because we don't wait after the final attempt
            return cumulativeWaitBeforeAttempt(maxAttempts - 1);
        }

        /**
         * Get backoff delay for a specific retry attempt.
         * @param attemptNumber 0-based attempt number
         * @return delay in milliseconds
         */
        long getBackoffDelay(int attemptNumber) {
            long delay = initialInterval * (long) Math.pow(multiplier, attemptNumber);
            return Math.min(delay, maxInterval);
        }

        /**
         * Get list of all retry delays for logging/debugging.
         */
        List<Long> getAllRetryDelays() {
            List<Long> delays = new ArrayList<>();
            for (int i = 0; i < maxAttempts; i++) {
                delays.add(getBackoffDelay(i));
            }
            return delays;
        }

        int getMaxAttempts() {
            return maxAttempts;
        }
    }
}
