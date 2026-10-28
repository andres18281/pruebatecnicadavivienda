package com.techdocs.notify.infrastructure;

import com.techdocs.contracts.DocumentProgressEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ProgressEventListener {

    private final StatusWebSocketHandler hub;

    public ProgressEventListener(StatusWebSocketHandler hub) {
        this.hub = hub;
    }

    @RabbitListener(queues = "#{progressQueue.name}")
    public void onProgress(DocumentProgressEvent event) {
        hub.broadcast(event);
    }
}
