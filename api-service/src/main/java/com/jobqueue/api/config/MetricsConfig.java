package com.jobqueue.api.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter jobsSubmittedCounter(MeterRegistry registry) {
        return Counter.builder("jobs_submitted_total")
                .description("Total number of jobs submitted via API")
                .tag("service", "api")
                .register(registry);
    }

    @Bean
    public Counter jobsSubmissionFailedCounter(MeterRegistry registry) {
        return Counter.builder("jobs_submission_failed_total")
                .description("Total number of failed job submissions")
                .tag("service", "api")
                .register(registry);
    }
}
