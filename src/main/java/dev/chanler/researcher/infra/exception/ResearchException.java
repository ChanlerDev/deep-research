package dev.chanler.researcher.infra.exception;

/**
 * @author: Chanler
 */
public class ResearchException extends RuntimeException {

    public ResearchException(String message) {
        super(message);
    }

    public ResearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
