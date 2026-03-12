package com.jobqueue.api.dto;

import com.jobqueue.api.model.Job;
import com.jobqueue.api.model.JobStatus;
import java.time.Instant;

/**
 * DTO for job status responses returned to clients.
 *
 * We use a separate DTO (not the domain model directly) because:
 * 1. We control exactly what fields the client sees
 * 2. Internal model changes don't break the API contract
 * 3. We can format/transform data for the client
 */
public class JobResponse {

    private String jobId;
    private JobStatus status;
    private String result;
    private String failureReason;
    private int retryCount;
    private Instant createdAt;
    private Instant updatedAt;

    public JobResponse() {
    }

    public static JobResponse fromJob(Job job) {
        JobResponse response = new JobResponse();
        response.jobId = job.getJobId();
        response.status = job.getStatus();
        response.result = job.getResult();
        response.failureReason = job.getFailureReason();
        response.retryCount = job.getRetryCount();
        response.createdAt = job.getCreatedAt();
        response.updatedAt = job.getUpdatedAt();
        return response;
    }

    // --- Getters ---

    public String getJobId() {
        return jobId;
    }

    public JobStatus getStatus() {
        return status;
    }

    public String getResult() {
        return result;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
