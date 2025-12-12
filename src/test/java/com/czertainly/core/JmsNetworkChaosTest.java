package com.czertainly.core;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.core.messaging.model.EventMessage;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class JmsNetworkChaosTest extends JmsResilienceTests {

    @Test
    void testConnectionIsUp() {
        eventProducer.sendMessage(new EventMessage(ResourceEvent.CERTIFICATE_DISCOVERED, Resource.DISCOVERY, UUID.randomUUID(), "testData"));
    }

    @Test
    void testConnectionDownForShortTime() throws Exception {
        int outageSeconds = 2;
        // Retry config from application.yml: initial=500ms, multiplier=2, maxAttempts=5
        // Expected retry timeline: 0ms (fail) -> 500ms (fail) -> 1500ms (fail) -> 3500ms (success after 2s restoration)
        // Conservative timing: expect at least 3 retry cycles (0 + 500 + 1000 = 1500ms) + connection time
        int expectedMinDurationMs = 2000; // Conservative: at least 2 seconds with tolerance

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        try {
            // 1. Simulate connection outage
            proxy.disable();
            logger.info("=== Connection DOWN for {} seconds ===", outageSeconds);

            // 2. Schedule connection restoration BEFORE 4th retry happens
            var restoreFuture = scheduler.schedule(() -> {
                try {
                    proxy.enable();
                    logger.info("=== Connection RESTORED ===");
                } catch (Exception e) {
                    logger.error("Failed to restore connection", e);
                    throw new RuntimeException(e);
                }
            }, outageSeconds, TimeUnit.SECONDS);

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
            restoreFuture.get(1, TimeUnit.SECONDS);

            // 5. Verify that retry actually happened
            // Should take at least 2 seconds (multiple retry attempts before restoration)
            Assertions.assertTrue(duration >= expectedMinDurationMs,
                    "Message should be sent after retry attempts (took " + duration + "ms, expected >=" + expectedMinDurationMs + "ms)");

            logger.info("=== Message sent successfully after {} ms (retry attempts) ===", duration);

        } finally {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("Scheduler did not terminate in time");
            }
        }
    }

    @Test
    void testConnectionLatencyShort() throws IOException {
        int latencyMs = 2000;
        int jitterMs = 500;
        String toxicName = "latency-toxic";

        // Add latency toxic (2000ms ± 500ms)
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
        // Expected: latency causes delay, but retry handles it
        logger.info("=== Message sent in {} ms (with {}ms latency) ===", duration, latencyMs);

        // Optional: verify duration is reasonable (not too fast, not timed out)
        Assertions.assertTrue(duration >= latencyMs - jitterMs - 500,
                "Send should take at least the latency time minus jitter and tolerance");

    }

    @Test
    void testConnectionTimeoutShort() throws Exception {
        int timeoutMs = 500; // Short timeout per attempt
        String toxicName = "timeout-cut";

        // Add timeout toxic - each connection attempt will timeout after 500ms
        proxy.toxics().timeout(toxicName, ToxicDirection.UPSTREAM, timeoutMs);
        logger.info("=== Connection timeout {} ms ===", timeoutMs);

        // First message should fail after all retry attempts are exhausted
        // Retry config: initial=500ms, multiplier=2, maxAttempts=5
        // Expected: 5 attempts, each timing out after 500ms
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
        // With 500ms timeout per attempt and retry delays, should take at least 2 seconds total
        int expectedMinDuration = timeoutMs * 2; // At least 2 timeout cycles
        Assertions.assertTrue(duration >= expectedMinDuration,
                "Should have retried multiple times (took " + duration + "ms, expected >=" + expectedMinDuration + "ms)");

        logger.info("=== Message failed after {} ms and {} retry attempts ===", duration, "5");

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
}
