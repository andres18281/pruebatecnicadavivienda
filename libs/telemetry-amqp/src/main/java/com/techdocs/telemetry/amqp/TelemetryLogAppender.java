package com.techdocs.telemetry.amqp;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

final class TelemetryLogAppender extends UnsynchronizedAppenderBase<ILoggingEvent> implements SmartLifecycle {

    private final TelemetryPublisher publisher;
    private volatile boolean running;

    TelemetryLogAppender(TelemetryPublisher publisher) {
        this.publisher = publisher;
        setName("telemetry-amqp");
    }

    @Override
    protected void append(ILoggingEvent event) {
        publisher.submitLog(event);
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        super.start();
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.addAppender(this);
        running = true;
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.detachAppender(this);
        super.stop();
        publisher.shutdown();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
