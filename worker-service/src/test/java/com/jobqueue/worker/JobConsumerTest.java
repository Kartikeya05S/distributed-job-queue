package com.jobqueue.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jobqueue.worker.consumer.JobConsumer;
import com.jobqueue.worker.handler.JobHandler;
import com.jobqueue.worker.model.Job;
import com.jobqueue.worker.model.JobStatus;
import com.jobqueue.worker.service.JobStateService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobConsumerTest {

    @Mock private JobStateService jobStateService;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private Counter jobsProcessedCounter;
    @Mock private Counter jobsFailedCounter;
    @Mock private Counter jobsRetriedCounter;
    @Mock private Counter jobsDlqCounter;
    @Mock private Timer jobProcessingTimer;
    @Mock private Acknowledgment acknowledgment;

    private ObjectMapper objectMapper;
    private JobConsumer jobConsumer;
    private StubJobHandler emailHandler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        emailHandler = new StubJobHandler("email", "Email sent to user@test.com");

        // Timer.record(Supplier) needs to actually call the supplier.
        // Lenient because not all test paths reach the timer.
        lenient().when(jobProcessingTimer.record(any(java.util.function.Supplier.class)))
                .thenAnswer(invocation -> {
                    java.util.function.Supplier<?> supplier = invocation.getArgument(0);
                    return supplier.get();
                });

        jobConsumer = new JobConsumer(
                List.of(emailHandler),
                jobStateService,
                kafkaTemplate,
                objectMapper,
                jobsProcessedCounter,
                jobsFailedCounter,
                jobsRetriedCounter,
                jobsDlqCounter,
                jobProcessingTimer,
                3,    // maxRetries
                10,   // baseBackoffMs (short for tests)
                "job_queue_dlq"
        );
    }

    @Test
    void consume_validJob_shouldProcessAndAcknowledge() throws Exception {
        Job job = createTestJob("job-1", "email", 0);
        String message = objectMapper.writeValueAsString(job);

        jobConsumer.consume(message, 0, 0L, acknowledgment);

        verify(jobStateService).updateStatus(any(Job.class), eq(JobStatus.PROCESSING));
        verify(jobStateService).markCompleted(any(Job.class), eq("Email sent to user@test.com"));
        verify(jobsProcessedCounter).increment();
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_unknownJobType_shouldSendToDLQAndAcknowledge() throws Exception {
        Job job = createTestJob("job-2", "unknown_type", 0);
        String message = objectMapper.writeValueAsString(job);

        jobConsumer.consume(message, 0, 0L, acknowledgment);

        verify(jobStateService).markFailed(any(Job.class), contains("Unknown job type"));
        verify(jobsDlqCounter).increment();
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_handlerThrows_shouldRetryWhenUnderMaxRetries() throws Exception {
        // Use a handler that throws
        StubJobHandler failingHandler = new StubJobHandler("email", null);
        failingHandler.shouldThrow = true;

        when(jobProcessingTimer.record(any(java.util.function.Supplier.class)))
                .thenAnswer(invocation -> {
                    java.util.function.Supplier<?> supplier = invocation.getArgument(0);
                    return supplier.get();
                });

        jobConsumer = new JobConsumer(
                List.of(failingHandler),
                jobStateService, kafkaTemplate, objectMapper,
                jobsProcessedCounter, jobsFailedCounter, jobsRetriedCounter, jobsDlqCounter,
                jobProcessingTimer, 3, 10, "job_queue_dlq"
        );

        Job job = createTestJob("job-3", "email", 0);
        String message = objectMapper.writeValueAsString(job);

        jobConsumer.consume(message, 0, 0L, acknowledgment);

        verify(jobsFailedCounter).increment();
        verify(jobsRetriedCounter).increment();
        verify(kafkaTemplate).send(eq("job_queue"), eq("job-3"), anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_handlerThrows_shouldSendToDLQWhenMaxRetriesExceeded() throws Exception {
        StubJobHandler failingHandler = new StubJobHandler("email", null);
        failingHandler.shouldThrow = true;

        when(jobProcessingTimer.record(any(java.util.function.Supplier.class)))
                .thenAnswer(invocation -> {
                    java.util.function.Supplier<?> supplier = invocation.getArgument(0);
                    return supplier.get();
                });

        jobConsumer = new JobConsumer(
                List.of(failingHandler),
                jobStateService, kafkaTemplate, objectMapper,
                jobsProcessedCounter, jobsFailedCounter, jobsRetriedCounter, jobsDlqCounter,
                jobProcessingTimer, 3, 10, "job_queue_dlq"
        );

        // retryCount already at max
        Job job = createTestJob("job-4", "email", 3);
        String message = objectMapper.writeValueAsString(job);

        jobConsumer.consume(message, 0, 0L, acknowledgment);

        verify(jobsFailedCounter).increment();
        verify(jobsDlqCounter).increment();
        verify(kafkaTemplate).send(eq("job_queue_dlq"), eq("job-4"), anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_invalidJson_shouldAcknowledgeWithoutCrashing() {
        jobConsumer.consume("not-valid-json", 0, 0L, acknowledgment);

        verify(jobsFailedCounter).increment();
        verify(acknowledgment).acknowledge();
    }

    private Job createTestJob(String jobId, String jobType, int retryCount) {
        Job job = new Job();
        job.setJobId(jobId);
        job.setJobType(jobType);
        job.setPayload(Map.of("to", "user@test.com", "subject", "Test"));
        job.setStatus(JobStatus.QUEUED);
        job.setRetryCount(retryCount);
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        return job;
    }

    /**
     * Simple stub handler for testing — avoids Thread.sleep() delays.
     */
    static class StubJobHandler implements JobHandler {
        private final String jobType;
        private final String result;
        boolean shouldThrow = false;

        StubJobHandler(String jobType, String result) {
            this.jobType = jobType;
            this.result = result;
        }

        @Override
        public String getJobType() { return jobType; }

        @Override
        public String handle(Job job) throws Exception {
            if (shouldThrow) throw new RuntimeException("Simulated failure");
            return result;
        }
    }
}
