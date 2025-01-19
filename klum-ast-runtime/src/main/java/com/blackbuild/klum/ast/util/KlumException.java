package com.blackbuild.klum.ast.util;

/**
 * Base exception for errors in the Klum Framework.
 */
public class KlumException extends RuntimeException {
    public KlumException(String message, Throwable cause) {
        super(message, cause);
    }
}
