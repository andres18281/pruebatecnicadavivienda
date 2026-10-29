package com.techdocs.resilience;

public final class OpenCircuitException extends RuntimeException {

    private final String dependency;

    public OpenCircuitException(String dependency, Throwable cause) {
        super("Circuito abierto para " + dependency, cause);
        this.dependency = dependency;
    }

    public String dependency() {
        return dependency;
    }
}
