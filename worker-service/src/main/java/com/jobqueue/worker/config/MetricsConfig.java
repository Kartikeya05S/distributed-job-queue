package com.jobqueue.worker.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Custom Prometheus metrics for job processing observability.
 *
 * These metrics are scraped by Prometheus and visualized in Grafana.
 * In production, these are critical for:
 * - Alerting on failure spikes
 * - Capacity planning (throughput trends)
 * - SLA monitoring (latency percentiles)
 */
@Configuration
public class MetricsConfig {

    @Bean
    public Counter jobsProcessedCounter(MeterRegistry registry) {
        return Counter.builder("jobs_processed_total")
                .description("Total number of successfully processed jobs")
                .tag("service", "worker")
                .register(registry);
    }

    @Bean
    public Counter jobsFailedCounter(MeterRegistry registry) {
        return Counter.builder("jobs_failed_total")
                .description("Total number of failed job processing attempts")
                .tag("service", "worker")
                .register(registry);
    }

    @Bean
    public Counter jobsRetriedCounter(MeterRegistry registry) {
        return Counter.builder("jobs_retried_total")
                .description("Total number of job retries")
                .tag("service", "worker")
                .register(registry);
    }

    @Bean
    public Counter jobsDlqCounter(MeterRegistry registry) {
        return Counter.builder("jobs_dead_letter_total")
                .description("Total number of jobs sent to dead letter queue")
                .tag("service", "worker")
                .register(registry);
    }

    @Bean
    public Timer jobProcessingTimer(MeterRegistry registry) {
        return Timer.builder("job_processing_duration_seconds")
                .description("Time taken to process a job")
                .tag("service", "worker")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }
}
