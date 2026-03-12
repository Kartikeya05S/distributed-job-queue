package com.jobqueue.worker.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobqueue.worker.handler.JobHandler;
import com.jobqueue.worker.model.Job;
import com.jobqueue.worker.model.JobStatus;
import com.jobqueue.worker.service.JobStateService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Main Kafka consumer that processes jobs from the queue.
 *
 * ARCHITECTURE:
 *
 * 1. Consumer Group: All worker instances share group "job-workers".
 *    Kafka assigns each partition to exactly one worker in the group.
 *    This guarantees each job is processed by exactly one worker.
 *
 * 2. Handler Registry: Job handlers register themselves by type.
 *    The consumer looks up the handler using jobType and delegates.
 *    Adding a new job type = implementing JobHandler interface.
 *
 * 3. Retry Logic: On failure, retries with exponential backoff.
 *    After MAX_RETRIES, the job goes to the Dead Letter Queue.
 *
 * 4. Manual Acknowledgment: We only ack AFTER processing succeeds
 *    (or after sending to DLQ). If a worker crashes mid-processing,
 *    the message is redelivered.
 *
 * 5. Observability: Prometheus counters and timers track throughput,
 *    failure rates, and processing latency.
 */
@Component
public class JobConsumer {

    private static final Logger log = LoggerFactory.getLogger(JobConsumer.class);

    private final int maxRetries;
    private final long baseBackoffMs;
    private final String dlqTopic;

    private final Map<String, JobHandler> handlerRegistry;
    private final JobStateService jobStateService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Counter jobsProcessedCounter;
    private final Counter jobsFailedCounter;
    private final Counter jobsRetriedCounter;
    private final Counter jobsDlqCounter;
    private final Timer jobProcessingTimer;

    public JobConsumer(List<JobHandler> handlers,
                       JobStateService jobStateService,
                       KafkaTemplate<String, String> kafkaTemplate,
                       ObjectMapper objectMapper,
                       Counter jobsProcessedCounter,
                       Counter jobsFailedCounter,
                       Counter jobsRetriedCounter,
                       Counter jobsDlqCounter,
                       Timer jobProcessingTimer,
                       @Value("${job.retry.max-attempts:3}") int maxRetries,
                       @Value("${job.retry.base-backoff-ms:1000}") long baseBackoffMs,
                       @Value("${job.dlq.topic:job_queue_dlq}") String dlqTopic) {
        this.handlerRegistry = handlers.stream()
                .collect(Collectors.toMap(JobHandler::getJobType, Function.identity()));
        this.jobStateService = jobStateService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.jobsProcessedCounter = jobsProcessedCounter;
        this.jobsFailedCounter = jobsFailedCounter;
        this.jobsRetriedCounter = jobsRetriedCounter;
        this.jobsDlqCounter = jobsDlqCounter;
        this.jobProcessingTimer = jobProcessingTimer;
        this.maxRetries = maxRetries;
        this.baseBackoffMs = baseBackoffMs;
        this.dlqTopic = dlqTopic;

        log.info("Registered job handlers: {}", handlerRegistry.keySet());
        log.info("Retry config: maxRetries={}, baseBackoffMs={}, dlqTopic={}", maxRetries, baseBackoffMs, dlqTopic);
    }

    @KafkaListener(topics = "job_queue", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String message,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset,
                        Acknowledgment acknowledgment) {
        Job job = null;
        try {
            job = objectMapper.readValue(message, Job.class);
            log.info("Received job: jobId={}, type={}, retryCount={}, partition={}, offset={}",
                    job.getJobId(), job.getJobType(), job.getRetryCount(), partition, offset);

            jobStateService.updateStatus(job, JobStatus.PROCESSING);

            JobHandler handler = handlerRegistry.get(job.getJobType());
            if (handler == null) {
                log.error("No handler found for job type: {}", job.getJobType());
                jobStateService.markFailed(job, "Unknown job type: " + job.getJobType());
                sendToDeadLetterQueue(job);
                acknowledgment.acknowledge();
                return;
            }

            // Execute with timing — measures processing latency for Prometheus
            final Job finalJob = job;
            String result = jobProcessingTimer.record(() -> {
                try {
                    return handler.handle(finalJob);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            jobStateService.markCompleted(job, result);
            jobsProcessedCounter.increment();
            log.info("Job completed: jobId={}, result={}", job.getJobId(), result);

            acknowledgment.acknowledge();

        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("Job processing failed: jobId={}, error={}",
                    job != null ? job.getJobId() : "unknown", cause.getMessage());

            jobsFailedCounter.increment();

            if (job != null) {
                handleFailure(job, cause);
            }

            acknowledgment.acknowledge();
        }
    }

    /**
     * Handles job failure with exponential backoff retry.
     *
     * BACKOFF STRATEGY:
     *   Retry 1: 1 second delay
     *   Retry 2: 2 second delay
     *   Retry 3: 4 second delay
     *   After 3 failures: Dead Letter Queue
     *
     * Formula: delay = BASE_BACKOFF_MS * 2^(retryCount - 1)
     *
     * Why exponential backoff?
     * - Gives transient failures (network blips, resource pressure) time to resolve
     * - Prevents retry storms that overload downstream services
     * - Standard practice at companies like AWS, Google, Stripe
     */
    private void handleFailure(Job job, Throwable e) {
        job.setFailureReason(e.getMessage());

        if (job.getRetryCount() < maxRetries) {
            job.setRetryCount(job.getRetryCount() + 1);
            job.setStatus(JobStatus.QUEUED);
            jobStateService.saveJob(job);

            long backoffMs = baseBackoffMs * (long) Math.pow(2, job.getRetryCount() - 1);
            log.warn("Retrying job: jobId={}, attempt={}/{}, backoff={}ms",
                    job.getJobId(), job.getRetryCount(), maxRetries, backoffMs);

            jobsRetriedCounter.increment();

            try {
                Thread.sleep(backoffMs);
                String retryMessage = objectMapper.writeValueAsString(job);
                kafkaTemplate.send("job_queue", job.getJobId(), retryMessage);
            } catch (Exception ex) {
                log.error("Failed to re-publish job for retry: jobId={}", job.getJobId(), ex);
                sendToDeadLetterQueue(job);
            }
        } else {
            log.error("Max retries exceeded: jobId={}, sending to DLQ", job.getJobId());
            sendToDeadLetterQueue(job);
        }
    }

    private void sendToDeadLetterQueue(Job job) {
        try {
            job.setStatus(JobStatus.DEAD_LETTER);
            jobStateService.saveJob(job);

            String dlqMessage = objectMapper.writeValueAsString(job);
            kafkaTemplate.send(dlqTopic, job.getJobId(), dlqMessage);
            jobsDlqCounter.increment();
            log.warn("Job sent to DLQ: jobId={}, reason={}", job.getJobId(), job.getFailureReason());
        } catch (Exception e) {
            log.error("Failed to send job to DLQ: jobId={}", job.getJobId(), e);
        }
    }
}
