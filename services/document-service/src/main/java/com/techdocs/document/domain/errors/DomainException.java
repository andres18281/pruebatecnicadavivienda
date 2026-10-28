package com.techdocs.document.domain.errors;

public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }

    public int httpStatus() {
        return switch (code) {
            case "UNSUPPORTED_FORMAT" -> 415;
            case "FILE_TOO_LARGE" -> 413;
            case "INVALID_METADATA" -> 422;
            case "NOT_FOUND" -> 404;
            case "CONFLICT" -> 409;
            default -> 400;
        };
    }

    public static final class InvalidMetadata extends DomainException {
        public InvalidMetadata(String message) {
            super("INVALID_METADATA", message);
        }
    }

    public static final class UnsupportedFormat extends DomainException {
        public UnsupportedFormat(String message) {
            super("UNSUPPORTED_FORMAT", message);
        }
    }

    public static final class FileTooLarge extends DomainException {
        public FileTooLarge(String message) {
            super("FILE_TOO_LARGE", message);
        }
    }

    public static final class NotFound extends DomainException {
        public NotFound(String message) {
            super("NOT_FOUND", message);
        }
    }

    public static final class Conflict extends DomainException {
        public Conflict(String message) {
            super("CONFLICT", message);
        }
    }

    public static final class InvalidTransition extends DomainException {
        public InvalidTransition(String message) {
            super("CONFLICT", message);
        }
    }
}
