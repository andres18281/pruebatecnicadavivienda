package com.techdocs.document.infrastructure.messaging;

import com.techdocs.contracts.DocumentProcessCommand;
import com.techdocs.contracts.DocumentProgressEvent;
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

    public void publishProgress(DocumentProgressEvent event) {
        rabbitTemplate.convertAndSend(RabbitTopologyConfig.EVENTS, RabbitTopologyConfig.PROGRESS_KEY, event);
    }
}
