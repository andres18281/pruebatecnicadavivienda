package com.techdocs.telemetry.amqp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@AutoConfiguration
@AutoConfigureBefore(RabbitAutoConfiguration.class)
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnProperty(name = "telemetry.amqp.enabled", matchIfMissing = true)
public class TelemetryAmqpAutoConfiguration {

    @Bean
    TopicExchange platformTelemetryExchange() {
        return new TopicExchange(TelemetryPublisher.EXCHANGE, true, false);
    }

    @Bean
    @ConditionalOnMissingBean(ConnectionFactory.class)
    ConnectionFactory connectionFactory(@Value("${RABBITMQ_URL}") String rabbitUrl) {
        URI uri = URI.create(rabbitUrl);
        int port = uri.getPort() == -1 ? 5672 : uri.getPort();
        CachingConnectionFactory factory = new CachingConnectionFactory(uri.getHost(), port);
        String[] userInfo = uri.getUserInfo().split(":", 2);
        factory.setUsername(URLDecoder.decode(userInfo[0], StandardCharsets.UTF_8));
        factory.setPassword(URLDecoder.decode(userInfo[1], StandardCharsets.UTF_8));
        return factory;
    }

    @Bean
    @ConditionalOnMissingBean(MessageConverter.class)
    Jackson2JsonMessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }

    @Bean
    @ConditionalOnMissingBean(RabbitTemplate.class)
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    @Bean
    TelemetryPublisher telemetryPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${spring.application.name:techdocs}") String service
    ) {
        return new TelemetryPublisher(rabbitTemplate, service);
    }

    @Bean
    TelemetryLogAppender telemetryLogAppender(TelemetryPublisher telemetryPublisher) {
        return new TelemetryLogAppender(telemetryPublisher);
    }

    @Bean
    ObservationHandler<Observation.Context> traceObservationHandler(TelemetryPublisher telemetryPublisher) {
        return new TraceObservationHandler(telemetryPublisher);
    }
}
