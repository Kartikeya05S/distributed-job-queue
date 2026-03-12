package com.jobqueue.worker;

import com.jobqueue.worker.handler.EmailJobHandler;
import com.jobqueue.worker.model.Job;
import com.jobqueue.worker.model.JobStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EmailJobHandlerTest {

    private final EmailJobHandler handler = new EmailJobHandler();

    @Test
    void getJobType_shouldReturnEmail() {
        assertEquals("email", handler.getJobType());
    }

    @Test
    void handle_shouldReturnResultWithRecipient() throws Exception {
        Job job = new Job();
        job.setJobId("test-1");
        job.setJobType("email");
        job.setPayload(Map.of("to", "user@example.com", "subject", "Hello"));
        job.setStatus(JobStatus.PROCESSING);
        job.setCreatedAt(Instant.now());

        String result = handler.handle(job);

        assertEquals("Email sent to user@example.com", result);
    }

    @Test
    void handle_withMissingTo_shouldUseDefaultUnknown() throws Exception {
        Job job = new Job();
        job.setJobId("test-2");
        job.setJobType("email");
        job.setPayload(Map.of("subject", "Hello"));
        job.setStatus(JobStatus.PROCESSING);
        job.setCreatedAt(Instant.now());

        String result = handler.handle(job);

        assertEquals("Email sent to unknown", result);
    }
}
