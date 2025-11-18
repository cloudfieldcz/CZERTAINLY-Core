package com.czertainly.core;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.stereotype.Component;

//@TestConfiguration
@Component
public class NoSchedulingConfig implements BeanFactoryPostProcessor {
//    @Bean
//    public static BeanFactoryPostProcessor disableScheduling() {
//        return bf -> {
//            if (bf instanceof DefaultListableBeanFactory) {
//                ((DefaultListableBeanFactory) bf).removeBeanDefinition("org.springframework.scheduling.annotation.internalScheduledAnnotationProcessor");
//            }
//        };
//    }

        @Override
        public void postProcessBeanFactory(final ConfigurableListableBeanFactory beanFactory) throws BeansException {
            for (String beanName : beanFactory.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class)) {
                ((DefaultListableBeanFactory)beanFactory).removeBeanDefinition(beanName);
            }
        }

}

