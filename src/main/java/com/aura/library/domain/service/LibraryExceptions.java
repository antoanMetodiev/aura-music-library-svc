package com.aura.library.domain.service;

/** The library's domain errors; GlobalExceptionHandler maps each to a status and code. */
public final class LibraryExceptions {

    private LibraryExceptions() {
    }

    /** Also thrown for someone else's private playlist — whether it exists is none of the caller's business. */
    public static class NotFound extends RuntimeException {
        private final String code;

        public NotFound(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /** Visible but not the caller's to change (another user's public playlist). */
    public static class Forbidden extends RuntimeException {
        public Forbidden(String message) {
            super(message);
        }
    }

    public static class LimitExceeded extends RuntimeException {
        public LimitExceeded(String message) {
            super(message);
        }
    }

    public static class CatalogUnavailable extends RuntimeException {
        public CatalogUnavailable(Throwable cause) {
            super("catalog-svc is unavailable", cause);
        }
    }
}
