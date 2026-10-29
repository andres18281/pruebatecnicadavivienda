package com.techdocs.telemetry.amqp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryFilterTest {

    @Test
    void ignoraLoggersDelPropioPublicador() {
        assertTrue(TelemetryFilter.isOwnLogger("com.techdocs.telemetry.amqp.TelemetryPublisher"));
        assertTrue(TelemetryFilter.isOwnLogger("org.springframework.amqp.rabbit.core.RabbitTemplate"));
        assertFalse(TelemetryFilter.isOwnLogger("com.techdocs.document.application.UploadDocumentService"));
    }

    @Test
    void ignoraObservacionesDeLaCola() {
        assertTrue(TelemetryFilter.isOwnObservation("spring.rabbit.listener"));
        assertTrue(TelemetryFilter.isOwnObservation(null));
        assertFalse(TelemetryFilter.isOwnObservation("http.server.requests"));
    }

    @Test
    void recortaMensajesLargos() {
        assertEquals(4, TelemetryFilter.truncate("abcdef", 4).length());
        assertEquals("", TelemetryFilter.truncate("   ", 10));
    }
}
