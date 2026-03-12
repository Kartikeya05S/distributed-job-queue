package com.jobqueue.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the API Service.
 *
 * This service is responsible for:
 * - Accepting job submissions via REST API
 * - Publishing jobs to Kafka (Phase 2)
 * - Serving job status queries from Redis (Phase 4)
 */
@SpringBootApplication
public class ApiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiServiceApplication.class, args);
    }
}
