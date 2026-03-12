package com.jobqueue.worker.handler;

import com.jobqueue.worker.model.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles email-type jobs.
 * Simulates sending an email with a small delay.
 */
@Component
public class EmailJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(EmailJobHandler.class);

    @Override
    public String getJobType() {
        return "email";
    }

    @Override
    public String handle(Job job) throws Exception {
        String to = (String) job.getPayload().getOrDefault("to", "unknown");
        String subject = (String) job.getPayload().getOrDefault("subject", "no subject");

        log.info("Sending email: to={}, subject={}", to, subject);

        // Simulate email sending delay
        Thread.sleep(1000);

        log.info("Email sent successfully: to={}", to);
        return "Email sent to " + to;
    }
}
