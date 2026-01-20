package com.hypeflow.sources;

/**
 * Exception thrown when a source client encounters an error.
 */
public class SourceClientException extends RuntimeException {

    private final String sourceId;

    public SourceClientException(String sourceId, String message) {
        super(message);
        this.sourceId = sourceId;
    }

    public SourceClientException(String sourceId, String message, Throwable cause) {
        super(message, cause);
        this.sourceId = sourceId;
    }

    public String getSourceId() {
        return sourceId;
    }
}