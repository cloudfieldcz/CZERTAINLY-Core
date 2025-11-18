package com.czertainly.core.config;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.RabbitMQContainer;

import java.sql.SQLException;

public class RabbitMQTestConfig {

    @Value("${spring.rabbitmq.port}")
    private String port;

    @Value("${spring.rabbitmq.username}")
    private String user;

    @Value("${spring.rabbitmq.password}")
    private String pass;

    @Bean
    public RabbitMQContainer setupRabbit() throws SQLException {
        int port = Integer.parseInt(this.port);
        RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4.2.0-management")
                .withExposedPorts(port)
                .withAdminUser(user)
                .withAdminPassword(pass)
                .withCreateContainerCmdModifier(cmd ->
                        cmd.getHostConfig().withPortBindings(
                                new PortBinding(
                                        Ports.Binding.bindPort(port),
                                        new ExposedPort(port)
                                )
                        )
                )
                ;
        rabbit.start();
        return rabbit;
    }
}
