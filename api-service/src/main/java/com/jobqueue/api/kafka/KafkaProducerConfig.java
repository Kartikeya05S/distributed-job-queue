package com.jobqueue.api.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer configuration for the API service.
 *
 * KEY CONCEPTS:
 * - Producer: The component that sends messages to Kafka topics
 * - Topic: A named feed/category of messages (like "job_queue")
 * - Partitions: A topic is split into partitions for parallelism.
 *   More partitions = more workers can process in parallel.
 * - StringSerializer: We serialize both key and value as strings (JSON).
 *   The key determines which partition a message goes to.
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // Ensure messages are durably written before acknowledging
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        // Enable idempotent producer to prevent duplicate messages on retry
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * Auto-create the job_queue topic with 3 partitions.
     * 3 partitions means up to 3 workers can process jobs in parallel.
     * Replication factor of 1 is fine for development; use 3 in production.
     */
    @Bean
    public NewTopic jobQueueTopic() {
        return new NewTopic("job_queue", 3, (short) 1);
    }

    @Bean
    public NewTopic deadLetterTopic() {
        return new NewTopic("job_queue_dlq", 1, (short) 1);
    }
}
