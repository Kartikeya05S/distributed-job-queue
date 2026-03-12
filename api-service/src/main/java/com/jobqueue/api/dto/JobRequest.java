package com.jobqueue.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * DTO for incoming job submission requests.
 *
 * Example:
 * {
 *   "jobType": "email",
 *   "payload": {
 *     "to": "user@email.com",
 *     "subject": "Welcome",
 *     "body": "Hello user"
 *   }
 * }
 */
public class JobRequest {

    @NotBlank(message = "jobType is required")
    private String jobType;

    @NotNull(message = "payload is required")
    private Map<String, Object> payload;

    public JobRequest() {
    }

    public JobRequest(String jobType, Map<String, Object> payload) {
        this.jobType = jobType;
        this.payload = payload;
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
}
