package com.jobqueue.worker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobqueue.worker.model.Job;
import com.jobqueue.worker.model.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Manages job state in Redis.
 *
 * REDIS DATA MODEL:
 *   Key:    job:{jobId}
 *   Value:  JSON string of the full Job object
 *   TTL:    24 hours (completed/failed jobs auto-expire)
 *
 * WHY REDIS?
 * - Sub-millisecond reads — fast status queries from the API
 * - Shared state — multiple API instances can read the same data
 * - TTL support — automatic cleanup of old job records
 * - Atomic operations — no race conditions on status updates
 */
@Service
public class JobStateService {

    private static final Logger log = LoggerFactory.getLogger(JobStateService.class);
    private static final String KEY_PREFIX = "job:";
    private static final Duration JOB_TTL = Duration.ofHours(24);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public JobStateService(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Save or update job state in Redis.
     */
    public void saveJob(Job job) {
        try {
            String key = KEY_PREFIX + job.getJobId();
            String value = objectMapper.writeValueAsString(job);
            redisTemplate.opsForValue().set(key, value, JOB_TTL);
            log.debug("Job state saved to Redis: jobId={}, status={}", job.getJobId(), job.getStatus());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize job for Redis: jobId={}", job.getJobId(), e);
        }
    }

    /**
     * Retrieve job state from Redis.
     */
    public Optional<Job> getJob(String jobId) {
        String key = KEY_PREFIX + jobId;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, Job.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize job from Redis: jobId={}", jobId, e);
            return Optional.empty();
        }
    }

    /**
     * Update job status and persist to Redis.
     */
    public void updateStatus(Job job, JobStatus status) {
        job.setStatus(status);
        job.setUpdatedAt(Instant.now());
        saveJob(job);
    }

    /**
     * Mark job as completed with a result.
     */
    public void markCompleted(Job job, String result) {
        job.setStatus(JobStatus.COMPLETED);
        job.setResult(result);
        job.setUpdatedAt(Instant.now());
        saveJob(job);
    }

    /**
     * Mark job as failed with a reason.
     */
    public void markFailed(Job job, String reason) {
        job.setStatus(JobStatus.FAILED);
        job.setFailureReason(reason);
        job.setUpdatedAt(Instant.now());
        saveJob(job);
    }
}
