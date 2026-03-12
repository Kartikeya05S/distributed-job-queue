package com.jobqueue.worker.handler;

import com.jobqueue.worker.model.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles image processing jobs.
 * Simulates image processing with a longer delay.
 */
@Component
public class ImageJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(ImageJobHandler.class);

    @Override
    public String getJobType() {
        return "image";
    }

    @Override
    public String handle(Job job) throws Exception {
        String imageUrl = (String) job.getPayload().getOrDefault("url", "unknown");
        String operation = (String) job.getPayload().getOrDefault("operation", "resize");

        log.info("Processing image: url={}, operation={}", imageUrl, operation);

        // Simulate image processing
        Thread.sleep(2000);

        log.info("Image processed successfully: url={}", imageUrl);
        return "Image processed: " + operation + " applied to " + imageUrl;
    }
}
