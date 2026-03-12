package com.jobqueue.api.model;

/**
 * Represents the lifecycle states of a job.
 *
 * State transitions:
 *   QUEUED → PROCESSING → COMPLETED
 *                       → FAILED (may retry → QUEUED again)
 *                       → DEAD_LETTER (max retries exceeded)
 */
public enum JobStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED,
    DEAD_LETTER
}
