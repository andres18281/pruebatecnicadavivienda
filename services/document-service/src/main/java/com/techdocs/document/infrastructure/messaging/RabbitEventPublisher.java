package com.techdocs.document.infrastructure.messaging;

import com.techdocs.contracts.DocumentProcessCommand;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public RabbitEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishProcess(DocumentProcessCommand command) {
        rabbitTemplate.convertAndSend(RabbitTopologyConfig.COMMANDS, RabbitTopologyConfig.PROCESS_KEY, command);
    }
}
