package com.jobqueue.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobqueue.api.controller.JobSubmissionException;
import com.jobqueue.api.dto.JobRequest;
import com.jobqueue.api.kafka.JobProducer;
import com.jobqueue.api.model.Job;
import io.micrometer.core.instrument.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Service layer for job lifecycle management.
 *
 * Backed by Redis — both API and worker services share the same
 * Redis instance as a single source of truth for job state.
 *
 * Flow:
 * 1. Client submits job → stored in Redis as QUEUED → published to Kafka
 * 2. Worker picks up job → updates Redis to PROCESSING
 * 3. Worker completes → updates Redis to COMPLETED
 * 4. Client queries status → reads from Redis
 */
@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);
    private static final String KEY_PREFIX = "job:";
    private static final Duration JOB_TTL = Duration.ofHours(24);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final JobProducer jobProducer;
    private final Counter jobsSubmittedCounter;
    private final Counter jobsSubmissionFailedCounter;

    public JobService(RedisTemplate<String, String> redisTemplate,
                      ObjectMapper objectMapper,
                      JobProducer jobProducer,
                      Counter jobsSubmittedCounter,
                      Counter jobsSubmissionFailedCounter) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.jobProducer = jobProducer;
        this.jobsSubmittedCounter = jobsSubmittedCounter;
        this.jobsSubmissionFailedCounter = jobsSubmissionFailedCounter;
    }

    public Job createJob(JobRequest request) {
        String jobId = UUID.randomUUID().toString();
        Job job = new Job(jobId, request.getJobType(), request.getPayload());

        try {
            saveToRedis(job);
            log.info("Job created: jobId={}, type={}", jobId, request.getJobType());

            jobProducer.sendJob(job);
            jobsSubmittedCounter.increment();
        } catch (Exception e) {
            jobsSubmissionFailedCounter.increment();
            log.error("Failed to submit job: jobId={}, error={}", jobId, e.getMessage());
            throw new JobSubmissionException("Failed to submit job: " + e.getMessage(), e);
        }

        return job;
    }

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

    private void saveToRedis(Job job) {
        try {
            String key = KEY_PREFIX + job.getJobId();
            String value = objectMapper.writeValueAsString(job);
            redisTemplate.opsForValue().set(key, value, JOB_TTL);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize job", e);
        }
    }
}
