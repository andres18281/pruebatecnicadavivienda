package com.techdocs.worker.infrastructure;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class RabbitTopologyConfig {

    public static final String COMMANDS = "documents.commands";
    public static final String RETRY = "documents.retry";
    public static final String EVENTS = "documents.events";
    public static final String PROCESS_QUEUE = "documents.process";
    public static final String RETRY_QUEUE = "documents.process.retry";
    public static final String DLQ = "documents.process.dlq";
    public static final String PROCESS_KEY = "document.process";
    public static final String STATUS_KEY = "document.status.changed";
    public static final String PROGRESS_KEY = "document.progress";

    @Bean
    Declarables documentTopology() {
        DirectExchange commands = new DirectExchange(COMMANDS, true, false);
        DirectExchange retry = new DirectExchange(RETRY, true, false);
        TopicExchange events = new TopicExchange(EVENTS, true, false);
        Queue process = new Queue(PROCESS_QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", RETRY,
                "x-dead-letter-routing-key", PROCESS_KEY
        ));
        Queue retryQueue = new Queue(RETRY_QUEUE, true, false, false, Map.of(
                "x-message-ttl", 30_000,
                "x-dead-letter-exchange", COMMANDS,
                "x-dead-letter-routing-key", PROCESS_KEY
        ));
        Queue dlq = new Queue(DLQ, true, false, false);
        Binding processBinding = BindingBuilder.bind(process).to(commands).with(PROCESS_KEY);
        Binding retryBinding = BindingBuilder.bind(retryQueue).to(retry).with(PROCESS_KEY);
        return new Declarables(commands, retry, events, process, retryQueue, dlq, processBinding, retryBinding);
    }
}
