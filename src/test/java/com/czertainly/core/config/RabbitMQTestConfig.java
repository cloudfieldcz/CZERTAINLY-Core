package com.czertainly.core.config;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;

@Profile("!toxiproxy-messaging-int-test")
public class RabbitMQTestConfig {

    @Value("${spring.rabbitmq.port}")
    protected String port;

    @Value("${spring.rabbitmq.username}")
    private String user;

    @Value("${spring.rabbitmq.password}")
    private String pass;

    @Bean
    public RabbitMQContainer setupRabbit() throws IOException, InterruptedException {
        return createRabbitMQContainer(null, port, user, pass);
    }

    public static RabbitMQContainer createRabbitMQContainer(Network network, String portStr, String user, String pass) throws IOException, InterruptedException {
        int port = Integer.parseInt(portStr);
        RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4.2-management")
                .withExposedPorts(port)
                .withAdminUser(user)
                .withAdminPassword(pass)
                .withNetworkAliases("broker") // default alias
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("/rabbitMQ-definitions.json"),
                        "/etc/rabbitmq/definitions.json"
                );
        if (network == null) {
            rabbit.withCreateContainerCmdModifier(cmd ->
                    cmd.getHostConfig().withPortBindings(
                            new PortBinding(
                                    Ports.Binding.bindPort(port),
                                    new ExposedPort(port)
                            ),
                            new PortBinding(
                                    Ports.Binding.bindPort(15672),
                                    new ExposedPort(15672)
                            )
                    )
            );
        }

        if (network != null) {
            rabbit.withNetwork(network);
            rabbit.withNetworkAliases("broker", "rabbitmq");
        }

        rabbit.start();
        rabbit.execInContainer("rabbitmqctl", "import_definitions", "/etc/rabbitmq/definitions.json");
        return rabbit;
    }
}
