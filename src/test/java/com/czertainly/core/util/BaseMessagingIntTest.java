package com.czertainly.core.util;

import com.czertainly.core.config.RabbitMQTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.RabbitMQContainer;

@SpringBootTest
@Import(RabbitMQTestConfig.class)
@ActiveProfiles({"messaging-int-test"})
public class BaseMessagingIntTest extends BaseSpringBootTest {

    @Autowired
    RabbitMQContainer rabbit;

    @Test
    void testContainerIsRunning() {
        assert rabbit.isRunning();
    }
}
