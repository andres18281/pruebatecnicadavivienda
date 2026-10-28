package com.techdocs.notify.infrastructure;

import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSocket
public class NotificationConfig implements WebSocketConfigurer {

    private final StatusWebSocketHandler handler;
    private final String wsPath;

    public NotificationConfig(StatusWebSocketHandler handler, @Value("${app.ws-path}") String wsPath) {
        this.handler = handler;
        this.wsPath = wsPath;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, wsPath).setAllowedOrigins("*");
    }

    @Bean
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
    MessageConverter jacksonMessageConverter(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setTypePrecedence(Jackson2JsonMessageConverter.TypePrecedence.INFERRED);
        return converter;
    }

    @Bean
    TopicExchange documentEvents() {
        return new TopicExchange("documents.events", true, false);
    }

    @Bean
    Queue statusQueue() {
        return new AnonymousQueue();
    }

    @Bean
    Binding statusBinding(Queue statusQueue, TopicExchange documentEvents) {
        return BindingBuilder.bind(statusQueue).to(documentEvents).with("document.status.#");
    }
}
