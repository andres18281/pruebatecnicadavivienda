package com.techdocs.worker.infrastructure;

import com.rabbitmq.client.Channel;
import com.techdocs.contracts.DocumentProcessCommand;
import com.techdocs.resilience.OpenCircuitException;
import com.techdocs.worker.application.ProcessDocumentService;
import com.techdocs.worker.domain.RetryDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class ProcessDocumentListener {

    private static final Logger log = LoggerFactory.getLogger(ProcessDocumentListener.class);

    private final ProcessDocumentService service;
    private final RabbitTemplate rabbitTemplate;
    private final int maxRetries;

    public ProcessDocumentListener(
            ProcessDocumentService service,
            RabbitTemplate rabbitTemplate,
            @Value("${app.max-retries}") int maxRetries
    ) {
        this.service = service;
        this.rabbitTemplate = rabbitTemplate;
        this.maxRetries = maxRetries;
    }

    @RabbitListener(queues = RabbitTopologyConfig.PROCESS_QUEUE, ackMode = "MANUAL")
    public void onMessage(
            DocumentProcessCommand command,
            Message message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) throws IOException {
        int previousFailures = attempts(message);
        try {
            log.info("evento=procesar documentId={}", command.documentId());
            service.process(command);
            channel.basicAck(deliveryTag, false);
            log.info("evento=procesado documentId={}", command.documentId());
        } catch (OpenCircuitException exception) {
            log.warn("Circuito abierto ({}) al procesar {}: {}", exception.dependency(), command.documentId(), exception.getMessage());
            try {
                republish(message, previousFailures);
            } catch (RuntimeException secondary) {
                log.error("No se pudo reencolar {}", command.documentId(), secondary);
                channel.basicNack(deliveryTag, false, false);
                return;
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception exception) {
            boolean handled = false;
            try {
                int failures = previousFailures + 1;
                log.warn("Fallo al procesar {} (intento {}): {}", command.documentId(), failures, exception.getMessage());
                if (RetryDecision.decide(previousFailures, maxRetries) == RetryDecision.Action.FAIL) {
                    service.fail(command, exception.getMessage(), failures);
                    sendToDlq(message);
                } else {
                    try {
                        service.recordAttempt(command, failures);
                    } catch (RuntimeException attemptError) {
                        log.warn("No se pudo registrar el intento {}: {}", command.documentId(), attemptError.getMessage());
                    }
                    republish(message, failures);
                }
                handled = true;
            } catch (RuntimeException secondary) {
                log.error("No se pudo reencolar {}", command.documentId(), secondary);
            }
            if (handled) {
                channel.basicAck(deliveryTag, false);
            } else {
                channel.basicNack(deliveryTag, false, false);
            }
        }
    }

    private void republish(Message message, int failures) {
        rabbitTemplate.send(RabbitTopologyConfig.RETRY, RabbitTopologyConfig.PROCESS_KEY, copy(message, failures));
    }

    private void sendToDlq(Message message) {
        rabbitTemplate.send("", RabbitTopologyConfig.DLQ, copy(message, attempts(message)));
    }

    private static Message copy(Message message, int failures) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(message.getMessageProperties().getContentType());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setHeader("x-attempts", failures);
        Object traceparent = message.getMessageProperties().getHeaders().get("traceparent");
        if (traceparent != null) {
            properties.setHeader("traceparent", traceparent);
        }
        return new Message(message.getBody(), properties);
    }

    private static int attempts(Message message) {
        Object raw = message.getMessageProperties().getHeaders().get("x-attempts");
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        Map<String, Object> headers = message.getMessageProperties().getHeaders();
        Object death = headers.get("x-death");
        if (death instanceof java.util.List<?> deaths && !deaths.isEmpty() && deaths.get(0) instanceof Map<?, ?> first) {
            Object count = first.get("count");
            if (count instanceof Number number) {
                return number.intValue();
            }
        }
        return 0;
    }
}
