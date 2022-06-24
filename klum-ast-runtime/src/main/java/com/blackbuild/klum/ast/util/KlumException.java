package com.blackbuild.klum.ast.util;

/**
 * Denotes an exception in the Klum code.
 */
public class KlumException extends RuntimeException {
    public KlumException() {
    }

    public KlumException(String message) {
        super(message);
    }

    public KlumException(String message, Throwable cause) {
        super(message, cause);
    }

    public KlumException(Throwable cause) {
        super(cause);
    }

    public KlumException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
