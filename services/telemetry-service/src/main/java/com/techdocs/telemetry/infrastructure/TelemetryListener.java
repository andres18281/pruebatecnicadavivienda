package com.techdocs.telemetry.infrastructure;

import com.rabbitmq.client.Channel;
import com.techdocs.contracts.LogRecordedEvent;
import com.techdocs.contracts.TraceRecordedEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class TelemetryListener {

    private final TelemetryStore store;

    public TelemetryListener(TelemetryStore store) {
        this.store = store;
    }

    @RabbitListener(queues = TelemetryInfrastructure.LOG_QUEUE, ackMode = "MANUAL")
    public void onLog(LogRecordedEvent event, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        accept(channel, tag, () -> store.saveLog(event));
    }

    @RabbitListener(queues = TelemetryInfrastructure.TRACE_QUEUE, ackMode = "MANUAL")
    public void onTrace(TraceRecordedEvent event, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        accept(channel, tag, () -> store.saveTrace(event));
    }

    private static void accept(Channel channel, long tag, Runnable action) throws IOException {
        try {
            action.run();
            channel.basicAck(tag, false);
        } catch (RuntimeException exception) {
            channel.basicNack(tag, false, false);
        }
    }
}
