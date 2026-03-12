package com.jobqueue.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Worker Service.
 *
 * Workers don't serve HTTP — they're pure Kafka consumers.
 * They sit in a consumer group and process jobs in parallel.
 */
@SpringBootApplication
public class WorkerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkerServiceApplication.class, args);
    }
}
