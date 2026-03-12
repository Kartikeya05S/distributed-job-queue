package com.jobqueue.worker.handler;

import com.jobqueue.worker.model.Job;

/**
 * Interface for job type handlers.
 *
 * Each job type (email, image, report, etc.) gets its own handler.
 * This follows the Strategy Pattern — the consumer routes to the
 * correct handler based on jobType.
 */
public interface JobHandler {

    /**
     * Returns the job type this handler supports (e.g., "email", "image").
     */
    String getJobType();

    /**
     * Process the job and return a result string.
     * Throws exception on failure (triggers retry logic).
     */
    String handle(Job job) throws Exception;
}
