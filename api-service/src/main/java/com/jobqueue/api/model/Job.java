package com.jobqueue.api.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

/**
 * Core domain model representing a job in the distributed queue.
 *
 * This is the internal representation stored in Redis and sent through Kafka.
 * It tracks the full lifecycle of a job: queued → processing → completed/failed.
 */
public class Job implements Serializable {

    private String jobId;
    private String jobType;
    private Map<String, Object> payload;
    private JobStatus status;
    private int retryCount;
    private String result;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;

    public Job() {
    }

    public Job(String jobId, String jobType, Map<String, Object> payload) {
        this.jobId = jobId;
        this.jobType = jobType;
        this.payload = payload;
        this.status = JobStatus.QUEUED;
        this.retryCount = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // --- Getters and Setters ---

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
