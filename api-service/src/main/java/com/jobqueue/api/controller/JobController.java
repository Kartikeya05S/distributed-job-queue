package com.jobqueue.api.controller;

import com.jobqueue.api.dto.JobRequest;
import com.jobqueue.api.dto.JobResponse;
import com.jobqueue.api.model.Job;
import com.jobqueue.api.service.JobService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for job submission and status queries.
 *
 * This is the single entry point for clients interacting with the job queue.
 * All job processing happens asynchronously — the client gets back a job ID
 * immediately and can poll for status.
 *
 * Endpoints:
 *   POST /api/jobs       → Submit a new job
 *   GET  /api/jobs/{id}  → Check job status
 */
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    /**
     * Submit a new job to the queue.
     *
     * Flow:
     * 1. Validate the request (jobType + payload required)
     * 2. Create the job (generates UUID, stores in job store)
     * 3. Return job ID + QUEUED status immediately
     *
     * The client does NOT wait for job processing — that's the whole point
     * of async job queues. Processing happens in worker services.
     */
    @PostMapping
    public ResponseEntity<JobResponse> submitJob(@Valid @RequestBody JobRequest request) {
        log.info("Received job submission: type={}", request.getJobType());

        Job job = jobService.createJob(request);

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)  // 202 — accepted for processing
                .body(JobResponse.fromJob(job));
    }

    /**
     * Query the status of an existing job.
     *
     * Clients use this to poll for job completion.
     * In a production system, you might also offer webhooks or SSE
     * for push-based notifications.
     */
    @GetMapping("/{jobId}")
    public ResponseEntity<JobResponse> getJobStatus(@PathVariable String jobId) {
        return jobService.getJob(jobId)
                .map(job -> ResponseEntity.ok(JobResponse.fromJob(job)))
                .orElse(ResponseEntity.notFound().build());
    }
}
