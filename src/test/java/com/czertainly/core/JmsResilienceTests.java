package com.czertainly.core;

import com.czertainly.core.config.JmsResilienceConfig;
import com.czertainly.core.config.RabbitMQTestConfig;
import com.czertainly.core.messaging.jms.listeners.AuditLogsListener;
import com.czertainly.core.messaging.jms.listeners.EventListener;
import com.czertainly.core.messaging.jms.producers.AuditLogsProducer;
import com.czertainly.core.messaging.jms.producers.EventProducer;
import com.czertainly.core.service.impl.AuditLogServiceImpl;
import com.czertainly.core.util.BaseMessagingIntTest;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.Toxic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;

@Tag("chaos")
@SpringBootTest
@ActiveProfiles({"toxiproxy-messaging-int-test"})
@Import(JmsResilienceConfig.class)
@Testcontainers
public abstract class JmsResilienceTests extends BaseMessagingIntTest {
    protected static final Logger logger = LoggerFactory.getLogger(JmsResilienceTests.class);

    private static final Network network = Network.newNetwork();

    @Container
    protected static final RabbitMQContainer rabbitMQContainer;

    static {
        try {
            rabbitMQContainer = RabbitMQTestConfig.createRabbitMQContainer(network, "5672", "guest", "guest");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Container
    protected static final ToxiproxyContainer toxiproxy = new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.5.0")
            .withNetwork(network)
            .dependsOn(rabbitMQContainer);


    protected static Proxy proxy;

    @MockitoSpyBean
    protected EventListener eventListener;
    @Autowired
    protected EventProducer eventProducer;

    @Autowired
    protected AuditLogsProducer auditLogsProducer;
    @Autowired
    protected AuditLogsListener auditLogsListener;
    @MockitoSpyBean
    protected AuditLogServiceImpl auditLogService;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) throws IOException {


        ToxiproxyClient client = new ToxiproxyClient(toxiproxy.getHost(), toxiproxy.getControlPort());
        proxy = client.createProxy("amqp-proxy", "0.0.0.0:8666", "broker:5672");
        String proxyUrl = String.format("amqp://%s:%d", toxiproxy.getHost(), toxiproxy.getMappedPort(8666));

        registry.add("spring.messaging.broker-url", () -> proxyUrl);
        registry.add("spring.rabbitmq.port", () -> toxiproxy.getMappedPort(8666));
        registry.add("spring.rabbitmq.host", toxiproxy::getHost);
    }

    @TestConfiguration
    @Profile("toxiproxy-messaging-int-test")
    static class ToxiProxyRabbitConfig {

        @Value("${spring.rabbitmq.port}")
        private String port;

        @Value("${spring.rabbitmq.username}")
        private String user;

        @Value("${spring.rabbitmq.password}")
        private String pass;

        @Bean
        @Primary
        public RabbitMQContainer setupRabbit() throws IOException, InterruptedException {
            return RabbitMQTestConfig.createRabbitMQContainer(network, port, user, pass);
        }
    }

    @BeforeEach
    void resetProxyToxics() throws IOException {
        ReflectionTestUtils.setField(auditLogsListener, "auditLogService", auditLogService);

        if (proxy != null) {
            for (Toxic toxic : proxy.toxics().getAll()) {
                toxic.remove();
            }
        }
    }
//
//    @Test
//    void testConnectionIsUp() {
//        eventProducer.sendMessage(new EventMessage(ResourceEvent.CERTIFICATE_DISCOVERED, Resource.DISCOVERY, UUID.randomUUID(), "testData"));
//    }
//
//    @Test
//    void testConnectionDownForShortTime() throws Exception {
//        int connectionDownForSeconds = 2;
//        proxy.disable();
//
//        logger.info("=== Connection CUT (Simulating outage) {} seconds ===", connectionDownForSeconds);
//
//        new Thread(() -> {
//            try {
//                Thread.sleep(connectionDownForSeconds * 1000);
//                proxy.enable();
//                logger.info("=== Connection RESTORED ===");
//            } catch (Exception e) {
//                logger.error("Error while restoring connection", e);
//            }
//        }).start();
//        boolean exceptionThrown = false;
//        try {
//            eventProducer.sendMessage(new EventMessage(ResourceEvent.CERTIFICATE_DISCOVERED, Resource.DISCOVERY, UUID.randomUUID(), "testData"));
//        } catch (Exception e) {
//            exceptionThrown = true;
//        }
//        Assertions.assertFalse(exceptionThrown);
//    }
//
//    @Test
//    void testConnectionLatencyShort() throws Exception {
//        int connectionDownForSeconds = 2;
//        proxy.toxics().latency("latency-cut", ToxicDirection.UPSTREAM, connectionDownForSeconds * 1000L)
//                .setJitter(500);
//        logger.info("=== Connection LATENCY (Simulating outage) for {} seconds ===", connectionDownForSeconds);
//
//        boolean exceptionThrown = false;
//        try {
//            eventProducer.sendMessage(new EventMessage(ResourceEvent.CERTIFICATE_DISCOVERED, Resource.DISCOVERY, UUID.randomUUID(), "testData"));
//        } catch (Exception e) {
//            exceptionThrown = true;
//        }
//        Assertions.assertFalse(exceptionThrown);
//    }
//
//
//    @Test
//    void testConnectionTimeoutShort() throws Exception {
//        int connectionDownForSeconds = 2;
//        String toxicName = "timeout-cut-" + connectionDownForSeconds;
//        proxy.toxics().timeout(toxicName, ToxicDirection.UPSTREAM, connectionDownForSeconds * 1000L);
//        logger.info("=== Connection timeout " + connectionDownForSeconds + "seconds ===");
//
//        boolean exceptionThrown = false;
//        try {
//            eventProducer.sendMessage(new EventMessage(ResourceEvent.CERTIFICATE_DISCOVERED, Resource.DISCOVERY, UUID.randomUUID(), "testData"));
//        } catch (Exception e) {
//            exceptionThrown = true;
//        }
//        Assertions.assertTrue(exceptionThrown);
//
//        new Thread(() -> {
//            try {
//                Thread.sleep((connectionDownForSeconds * 1000L) + 500);
//                proxy.toxics().get(toxicName).remove();
//                logger.info("=== Connection RESTORED (Toxic removed) ===");
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }).start();
//
//        exceptionThrown = false;
//        try {
//            eventProducer.sendMessage(new EventMessage(ResourceEvent.CERTIFICATE_DISCOVERED, Resource.DISCOVERY, UUID.randomUUID(), "testData"));
//        } catch (Exception e) {
//            exceptionThrown = true;
//        }
//        Assertions.assertFalse(exceptionThrown);
//    }
//
//    @Test
//    void testConnectionSlicer() throws Exception {
//        int connectionDownForSeconds = 5;
//        proxy.toxics().slicer("toxis-slicing", ToxicDirection.UPSTREAM, connectionDownForSeconds * 1000, 500);
//
//        logger.info("=== Connection is sliced {} seconds, delay 1 ===", connectionDownForSeconds);
//        boolean exceptionThrown = false;
//        try {
//            eventProducer.sendMessage(new EventMessage(ResourceEvent.CERTIFICATE_DISCOVERED, Resource.DISCOVERY, UUID.randomUUID(), "testData"));
//        } catch (Exception e) {
//            exceptionThrown = true;
//        }
//        Assertions.assertFalse(exceptionThrown);
//    }
//
//    @Test
//    void testConnectionSlowClose() throws Exception {
//        int connectionDownForSeconds = 1;
//        proxy.toxics().slowClose("slow-close", ToxicDirection.UPSTREAM, connectionDownForSeconds);
//
//        logger.info("=== Slow close {} seconds ===", connectionDownForSeconds);
//
//        // Try to send a message when a connection is down
//        // Expect that Producer handles this state - our strategy is to wait and try later.
//        boolean exceptionThrown = false;
//        try {
//            eventProducer.sendMessage(new EventMessage(ResourceEvent.CERTIFICATE_DISCOVERED, Resource.DISCOVERY, UUID.randomUUID(), "testData"));
//        } catch (Exception e) {
//            exceptionThrown = true;
//        }
//        Assertions.assertFalse(exceptionThrown);
//    }
//
//    @Test
//    @Disabled
//    void testConsumeWithLatency() throws IOException, EventException {
//        AuditLogMessage auditLogMessage = new AuditLogMessage(
//                LogRecord.builder()
//                        .audited(true)
//                        .message("test audit log")
//                        .resource(ResourceRecord.builder().type(Resource.CERTIFICATE).build())
//                        .actor(ActorRecord.builder().build())
//                        .build(),
//                AuditLogOutput.DATABASE
//        );
//
//        proxy.toxics().latency("latency", ToxicDirection.DOWNSTREAM, 2500);
//
//        auditLogsProducer.sendMessage(auditLogMessage);
//        Mockito.verify(auditLogService).log(any(LogRecord.class), any(AuditLogOutput.class));
//
//    }
//
//    @Test
//    @Disabled
//    void testConsumeWithLatencyHighJitter() throws IOException {
//        EventMessage eventMessage = new EventMessage(ResourceEvent.CERTIFICATE_DISCOVERED, Resource.DISCOVERY, UUID.randomUUID(), "testData");
//        eventProducer.sendMessage(eventMessage);
//        proxy.toxics().latency("latency-and-jitter", ToxicDirection.DOWNSTREAM, 2500).setJitter(500);
//
//        Mockito.verify(eventListener, Mockito.timeout(5000)).processMessage(refEq(eventMessage));
//    }
//
//    @Test
//    @Disabled
//    void testConsumeWithPeerReset() throws IOException {
//        EventMessage eventMessage = new EventMessage(ResourceEvent.CERTIFICATE_DISCOVERED, Resource.DISCOVERY, UUID.randomUUID(), "testData");
//        eventProducer.sendMessage(eventMessage);
//        proxy.toxics().resetPeer("peer-reset", ToxicDirection.DOWNSTREAM, 1000);
//
//        Mockito.verify(eventListener, Mockito.timeout(5000)).processMessage(refEq(eventMessage));
//    }
}