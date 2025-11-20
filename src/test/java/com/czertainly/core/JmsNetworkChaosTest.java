package com.czertainly.core;

import com.czertainly.api.exception.EventException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.AuditLogOutput;
import com.czertainly.api.model.core.logging.records.ActorRecord;
import com.czertainly.api.model.core.logging.records.LogRecord;
import com.czertainly.api.model.core.logging.records.ResourceRecord;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.core.messaging.model.AuditLogMessage;
import com.czertainly.core.messaging.model.EventMessage;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.refEq;

public class JmsNetworkChaosTest extends JmsResilienceTests {

    @Test
    void testConnectionIsUp() {
        eventProducer.sendMessage(new EventMessage(ResourceEvent.CERTIFICATE_DISCOVERED, Resource.DISCOVERY, UUID.randomUUID(), "testData"));
    }

    @Test
    void testConnectionDownForShortTime() {
        int connectionDownForSeconds = 2;
        try {
            proxy.disable();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        logger.info("=== Connection CUT (Simulating outage) {} seconds ===", connectionDownForSeconds);

        new Thread(() -> {
            try {
                Thread.sleep(connectionDownForSeconds * 1000);
                proxy.enable();
                logger.info("=== Connection RESTORED ===");
            } catch (Exception e) {
                logger.error("Error while restoring connection", e);
            }
        }).start();
        boolean exceptionThrown = false;
        try {
            eventProducer.sendMessage(new EventMessage(ResourceEvent.CERTIFICATE_DISCOVERED, Resource.DISCOVERY, UUID.randomUUID(), "testData"));
        } catch (Exception e) {
            exceptionThrown = true;
        }
        Assertions.assertFalse(exceptionThrown);
    }

    @Test
    void testConnectionLatencyShort() throws Exception {
        int connectionDownForSeconds = 2;
        proxy.toxics().latency("latency-cut", ToxicDirection.UPSTREAM, connectionDownForSeconds * 1000L)
                .setJitter(500);
        logger.info("=== Connection LATENCY (Simulating outage) for {} seconds ===", connectionDownForSeconds);

        boolean exceptionThrown = false;
        try {
            eventProducer.sendMessage(new EventMessage(ResourceEvent.CERTIFICATE_DISCOVERED, Resource.DISCOVERY, UUID.randomUUID(), "testData"));
        } catch (Exception e) {
            exceptionThrown = true;
        }
        Assertions.assertFalse(exceptionThrown);
    }


    @Test
    void testConnectionTimeoutShort() throws Exception {
        int connectionDownForSeconds = 2;
        String toxicName = "timeout-cut-" + connectionDownForSeconds;
        proxy.toxics().timeout(toxicName, ToxicDirection.UPSTREAM, connectionDownForSeconds * 1000L);
        logger.info("=== Connection timeout " + connectionDownForSeconds + "seconds ===");

        boolean exceptionThrown = false;
        try {
            eventProducer.sendMessage(new EventMessage(ResourceEvent.CERTIFICATE_DISCOVERED, Resource.DISCOVERY, UUID.randomUUID(), "testData"));
        } catch (Exception e) {
            exceptionThrown = true;
        }
        Assertions.assertTrue(exceptionThrown);

        new Thread(() -> {
            try {
                Thread.sleep((connectionDownForSeconds * 1000L) + 500);
                proxy.toxics().get(toxicName).remove();
                logger.info("=== Connection RESTORED (Toxic removed) ===");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        exceptionThrown = false;
        try {
            eventProducer.sendMessage(new EventMessage(ResourceEvent.CERTIFICATE_DISCOVERED, Resource.DISCOVERY, UUID.randomUUID(), "testData"));
        } catch (Exception e) {
            exceptionThrown = true;
        }
        Assertions.assertFalse(exceptionThrown);
    }

    @Test
    void testConnectionSlicer() throws Exception {
        int connectionDownForSeconds = 5;
        proxy.toxics().slicer("toxis-slicing", ToxicDirection.UPSTREAM, connectionDownForSeconds * 1000, 500);

        logger.info("=== Connection is sliced {} seconds, delay 1 ===", connectionDownForSeconds);
        boolean exceptionThrown = false;
        try {
            eventProducer.sendMessage(new EventMessage(ResourceEvent.CERTIFICATE_DISCOVERED, Resource.DISCOVERY, UUID.randomUUID(), "testData"));
        } catch (Exception e) {
            exceptionThrown = true;
        }
        Assertions.assertFalse(exceptionThrown);
    }

    @Test
    void testConnectionSlowClose() throws Exception {
        int connectionDownForSeconds = 1;
        proxy.toxics().slowClose("slow-close", ToxicDirection.UPSTREAM, connectionDownForSeconds);

        logger.info("=== Slow close {} seconds ===", connectionDownForSeconds);

        // Try to send a message when a connection is down
        // Expect that Producer handles this state - our strategy is to wait and try later.
        boolean exceptionThrown = false;
        try {
            eventProducer.sendMessage(new EventMessage(ResourceEvent.CERTIFICATE_DISCOVERED, Resource.DISCOVERY, UUID.randomUUID(), "testData"));
        } catch (Exception e) {
            exceptionThrown = true;
        }
        Assertions.assertFalse(exceptionThrown);
    }

    @Test
    @Disabled
    void testConsumeWithLatency() throws IOException, EventException {
        AuditLogMessage auditLogMessage = new AuditLogMessage(
                LogRecord.builder()
                        .audited(true)
                        .message("test audit log")
                        .resource(ResourceRecord.builder().type(Resource.CERTIFICATE).build())
                        .actor(ActorRecord.builder().build())
                        .build(),
                AuditLogOutput.DATABASE
        );

        proxy.toxics().latency("latency", ToxicDirection.DOWNSTREAM, 2500);

        auditLogsProducer.sendMessage(auditLogMessage);
        Mockito.verify(auditLogService).log(any(LogRecord.class), any(AuditLogOutput.class));

    }

    @Test
    @Disabled
    void testConsumeWithLatencyHighJitter() throws IOException {
        EventMessage eventMessage = new EventMessage(ResourceEvent.CERTIFICATE_DISCOVERED, Resource.DISCOVERY, UUID.randomUUID(), "testData");
        eventProducer.sendMessage(eventMessage);
        proxy.toxics().latency("latency-and-jitter", ToxicDirection.DOWNSTREAM, 2500).setJitter(500);

        Mockito.verify(eventListener, Mockito.timeout(5000)).processMessage(refEq(eventMessage));
    }

    @Test
    @Disabled
    void testConsumeWithPeerReset() throws IOException {
        EventMessage eventMessage = new EventMessage(ResourceEvent.CERTIFICATE_DISCOVERED, Resource.DISCOVERY, UUID.randomUUID(), "testData");
        eventProducer.sendMessage(eventMessage);
        proxy.toxics().resetPeer("peer-reset", ToxicDirection.DOWNSTREAM, 1000);

        Mockito.verify(eventListener, Mockito.timeout(5000)).processMessage(refEq(eventMessage));
    }
}
