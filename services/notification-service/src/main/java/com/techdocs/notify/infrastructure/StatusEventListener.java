package com.techdocs.notify.infrastructure;

import com.techdocs.contracts.DocumentStatusChangedEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class StatusEventListener {

    private final StatusWebSocketHandler hub;

    public StatusEventListener(StatusWebSocketHandler hub) {
        this.hub = hub;
    }

    @RabbitListener(queues = "#{statusQueue.name}")
    public void onStatus(DocumentStatusChangedEvent event) {
        hub.broadcast(event);
    }
}
