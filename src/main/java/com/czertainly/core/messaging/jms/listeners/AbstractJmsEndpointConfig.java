package com.czertainly.core.messaging.jms.listeners;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.retry.support.RetryTemplate;

import java.util.function.Supplier;

public abstract class AbstractJmsEndpointConfig<T> {

    @Autowired
    protected MessageConverter messageConverter;
    @Autowired
    protected MessageProcessor<T> listenerMessageProcessor;
    @Autowired
    protected RetryTemplate jmsRetryTemplate;

    public abstract SimpleJmsListenerEndpoint listenerEndpoint();

    public SimpleJmsListenerEndpoint listenerEndpointInternal(Supplier<String> endpointId, Supplier<String> destination,
                                                              Supplier<String> routingKey, Supplier<String> concurrency,
                                                              Class<T> messageClass) {
        SimpleJmsListenerEndpoint endpoint = new SimpleJmsListenerEndpoint();
        endpoint.setId(endpointId.get());
        endpoint.setDestination(destination.get());
        endpoint.setSelector("routingKey = '" + routingKey.get() + "'");
        endpoint.setConcurrency(concurrency.get());
        endpoint.setMessageListener(jmsMessage -> {
            jmsRetryTemplate.execute(context -> {
                try {
                    Object converted = messageConverter.fromMessage(jmsMessage);
                    T message = messageClass.cast(converted);
                    listenerMessageProcessor.processMessage(message);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to convert JMS message", e);
                }
                return null;
            });
        });
        return endpoint;
    }
}
