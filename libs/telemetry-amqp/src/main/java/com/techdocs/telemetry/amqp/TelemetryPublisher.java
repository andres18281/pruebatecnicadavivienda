package com.techdocs.telemetry.amqp;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.techdocs.contracts.LogRecordedEvent;
import com.techdocs.contracts.TraceRecordedEvent;
import io.micrometer.observation.Observation;
import org.slf4j.MDC;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class TelemetryPublisher {

    static final String EXCHANGE = "platform.telemetry";
    static final String LOG_KEY = "telemetry.log";
    static final String TRACE_KEY = "telemetry.trace";

    private static final ThreadLocal<Boolean> GUARD = ThreadLocal.withInitial(() -> false);

    private final RabbitTemplate rabbitTemplate;
    private final String service;
    private final ThreadPoolExecutor executor;

    TelemetryPublisher(RabbitTemplate rabbitTemplate, String service) {
        this.rabbitTemplate = rabbitTemplate;
        this.service = service;
        this.executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1000),
                runnable -> {
                    Thread thread = new Thread(runnable, "telemetry-amqp");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.DiscardPolicy()
        );
    }

    void submitLog(ILoggingEvent event) {
        if (GUARD.get() || TelemetryFilter.isOwnLogger(event.getLoggerName())) {
            return;
        }
        if (event.getLevel().toInt() < Level.INFO_INT) {
            return;
        }
        LogRecordedEvent payload = new LogRecordedEvent(
                UUID.randomUUID().toString(),
                service,
                event.getLevel().toString(),
                TelemetryFilter.truncate(event.getLoggerName(), 300),
                TelemetryFilter.truncate(event.getFormattedMessage(), 4000),
                traceId(event),
                spanId(event),
                Instant.now().toString()
        );
        enqueue(LOG_KEY, payload);
    }

    void submitTrace(Observation.Context context) {
        if (GUARD.get() || TelemetryFilter.isOwnObservation(context.getName())) {
            return;
        }
        String name = context.getContextualName() == null || context.getContextualName().isBlank()
                ? context.getName()
                : context.getContextualName();
        long durationMs = 0;
        Long startNanos = context.get(TraceObservationHandler.START_NANOS);
        if (startNanos != null && startNanos > 0) {
            durationMs = Math.max(0, (System.nanoTime() - startNanos) / 1_000_000);
        }
        TraceRecordedEvent payload = new TraceRecordedEvent(
                UUID.randomUUID().toString(),
                service,
                firstPresent(MDC.get("trace_id"), MDC.get("traceId"), UUID.randomUUID().toString()),
                firstPresent(MDC.get("span_id"), MDC.get("spanId"), ""),
                null,
                TelemetryFilter.truncate(name, 200),
                context.getError() == null ? "OK" : "ERROR",
                durationMs,
                Instant.now().toString()
        );
        enqueue(TRACE_KEY, payload);
    }

    void shutdown() {
        executor.shutdownNow();
    }

    private void enqueue(String routingKey, Object payload) {
        executor.execute(() -> {
            GUARD.set(true);
            try {
                rabbitTemplate.convertAndSend(EXCHANGE, routingKey, payload, message -> {
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return message;
                });
            } catch (RuntimeException ignored) {
                // La telemetria no puede tumbar la operacion de negocio.
            } finally {
                GUARD.remove();
            }
        });
    }

    private static String traceId(ILoggingEvent event) {
        String fromMdc = event.getMDCPropertyMap().get("trace_id");
        if (fromMdc == null) {
            fromMdc = event.getMDCPropertyMap().get("traceId");
        }
        return fromMdc;
    }

    private static String spanId(ILoggingEvent event) {
        String fromMdc = event.getMDCPropertyMap().get("span_id");
        if (fromMdc == null) {
            fromMdc = event.getMDCPropertyMap().get("spanId");
        }
        return fromMdc;
    }

    private static String firstPresent(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallback;
    }
}
