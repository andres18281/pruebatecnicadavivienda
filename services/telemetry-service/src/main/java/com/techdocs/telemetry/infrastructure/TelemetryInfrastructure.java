package com.techdocs.telemetry.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Configuration
public class TelemetryInfrastructure {

    static final String EXCHANGE = "platform.telemetry";
    static final String DLX = "platform.telemetry.dlx";
    static final String LOG_QUEUE = "telemetry.logs";
    static final String TRACE_QUEUE = "telemetry.traces";

    @Bean
    DataSource dataSource(@Value("${DATABASE_URL}") String databaseUrl) {
        DatabaseUrlParser.Parsed parsed = DatabaseUrlParser.parse(databaseUrl);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(parsed.jdbcUrl());
        config.setUsername(parsed.username());
        config.setPassword(parsed.password());
        config.setMaximumPoolSize(10);
        config.setPoolName("telemetry-service");
        return new HikariDataSource(config);
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
    MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }

    @Bean
    Declarables telemetryTopology() {
        TopicExchange exchange = new TopicExchange(EXCHANGE, true, false);
        TopicExchange deadLetter = new TopicExchange(DLX, true, false);
        Queue logs = durable(LOG_QUEUE, "telemetry.log");
        Queue traces = durable(TRACE_QUEUE, "telemetry.trace");
        Queue logsDlq = new Queue("telemetry.logs.dlq", true);
        Queue tracesDlq = new Queue("telemetry.traces.dlq", true);
        return new Declarables(
                exchange,
                deadLetter,
                logs,
                traces,
                logsDlq,
                tracesDlq,
                BindingBuilder.bind(logs).to(exchange).with("telemetry.log"),
                BindingBuilder.bind(traces).to(exchange).with("telemetry.trace"),
                BindingBuilder.bind(logsDlq).to(deadLetter).with("telemetry.log"),
                BindingBuilder.bind(tracesDlq).to(deadLetter).with("telemetry.trace")
        );
    }

    private static Queue durable(String name, String deadLetterKey) {
        return new Queue(name, true, false, false, Map.of(
                "x-dead-letter-exchange", DLX,
                "x-dead-letter-routing-key", deadLetterKey
        ));
    }
}
