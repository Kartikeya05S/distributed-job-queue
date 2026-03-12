package com.jobqueue.api.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobqueue.api.model.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes job messages to Kafka topics.
 *
 * WHY KAFKA?
 * - Decouples producers (API) from consumers (workers)
 * - Messages are persisted on disk — survives broker restarts
 * - Supports consumer groups for horizontal scaling
 * - Provides ordering guarantees within a partition
 *
 * MESSAGE KEY STRATEGY:
 * We use jobId as the Kafka message key. This means:
 * - All messages for the same job go to the same partition
 * - Within a partition, messages are strictly ordered
 * - This prevents race conditions on job state updates
 */
@Component
public class JobProducer {

    private static final Logger log = LoggerFactory.getLogger(JobProducer.class);
    private static final String TOPIC = "job_queue";
    private static final String DLQ_TOPIC = "job_queue_dlq";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public JobProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publish a job to the main job queue topic.
     * Uses jobId as key so all retries for the same job land on the same partition.
     */
    public void sendJob(Job job) {
        try {
            String message = objectMapper.writeValueAsString(job);
            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(TOPIC, job.getJobId(), message);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to send job to Kafka: jobId={}, error={}",
                            job.getJobId(), ex.getMessage());
                } else {
                    log.info("Job published to Kafka: jobId={}, partition={}, offset={}",
                            job.getJobId(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize job: jobId={}", job.getJobId(), e);
            throw new RuntimeException("Failed to serialize job", e);
        }
    }

    /**
     * Send a failed job to the Dead Letter Queue.
     * DLQ jobs are not automatically retried — they require manual inspection.
     */
    public void sendToDeadLetterQueue(Job job) {
        try {
            String message = objectMapper.writeValueAsString(job);
            kafkaTemplate.send(DLQ_TOPIC, job.getJobId(), message);
            log.warn("Job sent to DLQ: jobId={}, retryCount={}, reason={}",
                    job.getJobId(), job.getRetryCount(), job.getFailureReason());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize job for DLQ: jobId={}", job.getJobId(), e);
        }
    }
}
