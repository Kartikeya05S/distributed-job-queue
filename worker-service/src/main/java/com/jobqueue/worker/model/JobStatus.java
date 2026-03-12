package com.jobqueue.worker.model;

public enum JobStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED,
    DEAD_LETTER
}
