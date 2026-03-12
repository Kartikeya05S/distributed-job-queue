package com.jobqueue.api.controller;

/**
 * Thrown when a job cannot be submitted to the queue
 * (e.g., Kafka is unavailable, serialization failure).
 */
public class JobSubmissionException extends RuntimeException {

    public JobSubmissionException(String message) {
        super(message);
    }

    public JobSubmissionException(String message, Throwable cause) {
        super(message, cause);
    }
}
