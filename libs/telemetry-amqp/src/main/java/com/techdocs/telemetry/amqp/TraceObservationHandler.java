package com.techdocs.telemetry.amqp;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;

final class TraceObservationHandler implements ObservationHandler<Observation.Context> {

    private final TelemetryPublisher publisher;

    TraceObservationHandler(TelemetryPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return true;
    }

    @Override
    public void onStop(Observation.Context context) {
        publisher.submitTrace(context);
    }
}
