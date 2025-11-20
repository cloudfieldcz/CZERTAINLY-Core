package com.czertainly.core.messaging.jms.listeners;

import com.czertainly.core.messaging.jms.configuration.JmsConfig;
import com.czertainly.core.messaging.jms.configuration.MessagingProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.listener.MessageListenerContainer;
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
    @Autowired
    protected MessagingProperties messagingProperties;

    public abstract SimpleJmsListenerEndpoint listenerEndpoint();

    /**
     *
     * @param endpointId unique id for the endpoint
     * @param destination or Topic name in Azure ServiceBus
     * @param routingKey or Subscription name in Azure ServiceBus
     * @param concurrency number of threads
     * @param messageClass type of message to be processed
     * @return endpoint to register in Spring context
     */
    public SimpleJmsListenerEndpoint listenerEndpointInternal(Supplier<String> endpointId, Supplier<String> destination,
                                                              Supplier<String> routingKey, Supplier<String> concurrency,
                                                              Class<T> messageClass) {
        SimpleJmsListenerEndpoint endpoint;
        if (messagingProperties.name() == MessagingProperties.BrokerName.SERVICEBUS) {
            endpoint = new SimpleJmsListenerEndpoint() {
                @Override
                public void setupListenerContainer(MessageListenerContainer listenerContainer) {
                    super.setupListenerContainer(listenerContainer);
                    if (listenerContainer instanceof DefaultMessageListenerContainer container) {
                        container.setSubscriptionShared(true);// Shared must be set to allow concurrency
                        container.setClientId(endpointId.get());
                        container.setSubscriptionDurable(true);
                        container.setDurableSubscriptionName(endpointId.get());
                    }
                }
            };
        } else {
            endpoint = new SimpleJmsListenerEndpoint();
        }

        endpoint.setId(endpointId.get());

        if (messagingProperties.name() == MessagingProperties.BrokerName.SERVICEBUS) {
            endpoint.setSubscription(routingKey.get());
            endpoint.setSelector(JmsConfig.ROUTING_KEY + " = '" + routingKey.get() + "'");
        }

        endpoint.setDestination(destination.get());
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
