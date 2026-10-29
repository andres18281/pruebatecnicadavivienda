package com.techdocs.telemetry.amqp;

final class TelemetryFilter {

    private TelemetryFilter() {
    }

    static boolean isOwnLogger(String loggerName) {
        return loggerName != null && (
                loggerName.startsWith("com.techdocs.telemetry")
                        || loggerName.startsWith("org.springframework.amqp")
                        || loggerName.startsWith("com.rabbitmq")
        );
    }

    static boolean isOwnObservation(String name) {
        if (name == null || name.isBlank()) {
            return true;
        }
        return name.startsWith("spring.rabbit")
                || name.contains("amqp")
                || name.startsWith("tasks.scheduled");
    }

    static String truncate(String value, int max) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
