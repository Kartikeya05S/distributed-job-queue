package com.jobqueue.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jobqueue.worker.model.Job;
import com.jobqueue.worker.model.JobStatus;
import com.jobqueue.worker.service.JobStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobStateServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private JobStateService jobStateService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        jobStateService = new JobStateService(redisTemplate, objectMapper);
    }

    @Test
    void saveJob_shouldSerializeAndStoreInRedisWithTTL() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        Job job = createTestJob("job-1", "email");

        jobStateService.saveJob(job);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

        assertEquals("job:job-1", keyCaptor.getValue());
        assertEquals(Duration.ofHours(24), ttlCaptor.getValue());

        // Verify stored JSON can be deserialized back
        Job deserialized = objectMapper.readValue(valueCaptor.getValue(), Job.class);
        assertEquals("job-1", deserialized.getJobId());
        assertEquals("email", deserialized.getJobType());
    }

    @Test
    void getJob_whenExists_shouldReturnDeserializedJob() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        Job original = createTestJob("job-2", "image");
        String json = objectMapper.writeValueAsString(original);
        when(valueOperations.get("job:job-2")).thenReturn(json);

        Optional<Job> result = jobStateService.getJob("job-2");

        assertTrue(result.isPresent());
        assertEquals("job-2", result.get().getJobId());
        assertEquals("image", result.get().getJobType());
    }

    @Test
    void getJob_whenNotExists_shouldReturnEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("job:nonexistent")).thenReturn(null);

        Optional<Job> result = jobStateService.getJob("nonexistent");

        assertTrue(result.isEmpty());
    }

    @Test
    void getJob_whenInvalidJson_shouldReturnEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("job:bad")).thenReturn("not-valid-json");

        Optional<Job> result = jobStateService.getJob("bad");

        assertTrue(result.isEmpty());
    }

    @Test
    void updateStatus_shouldSetStatusAndPersist() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        Job job = createTestJob("job-3", "report");

        jobStateService.updateStatus(job, JobStatus.PROCESSING);

        assertEquals(JobStatus.PROCESSING, job.getStatus());
        assertNotNull(job.getUpdatedAt());
        verify(valueOperations).set(eq("job:job-3"), anyString(), eq(Duration.ofHours(24)));
    }

    @Test
    void markCompleted_shouldSetStatusAndResult() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        Job job = createTestJob("job-4", "email");

        jobStateService.markCompleted(job, "Email sent to user@test.com");

        assertEquals(JobStatus.COMPLETED, job.getStatus());
        assertEquals("Email sent to user@test.com", job.getResult());
        verify(valueOperations).set(eq("job:job-4"), anyString(), eq(Duration.ofHours(24)));
    }

    @Test
    void markFailed_shouldSetStatusAndReason() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        Job job = createTestJob("job-5", "image");

        jobStateService.markFailed(job, "Connection timeout");

        assertEquals(JobStatus.FAILED, job.getStatus());
        assertEquals("Connection timeout", job.getFailureReason());
        verify(valueOperations).set(eq("job:job-5"), anyString(), eq(Duration.ofHours(24)));
    }

    private Job createTestJob(String jobId, String jobType) {
        Job job = new Job();
        job.setJobId(jobId);
        job.setJobType(jobType);
        job.setPayload(Map.of("key", "value"));
        job.setStatus(JobStatus.QUEUED);
        job.setRetryCount(0);
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        return job;
    }
}
