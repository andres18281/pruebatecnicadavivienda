package com.techdocs.telemetry.amqp;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;

final class TraceObservationHandler implements ObservationHandler<Observation.Context> {

    static final String START_NANOS = "techdocs.telemetry.startNanos";

    private final TelemetryPublisher publisher;

    TraceObservationHandler(TelemetryPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return true;
    }

    @Override
    public void onStart(Observation.Context context) {
        context.put(START_NANOS, System.nanoTime());
    }

    @Override
    public void onStop(Observation.Context context) {
        publisher.submitTrace(context);
    }
}
