package com.jobqueue.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobqueue.api.controller.JobController;
import com.jobqueue.api.dto.JobRequest;
import com.jobqueue.api.model.Job;
import com.jobqueue.api.service.JobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for the Job API controller.
 *
 * Uses @WebMvcTest to load only the web layer — no Kafka, Redis, or other
 * infrastructure needed. JobService is mocked.
 */
@WebMvcTest(JobController.class)
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JobService jobService;

    @Test
    void submitJob_shouldReturn202WithJobId() throws Exception {
        Job job = new Job("test-uuid", "email", Map.of("to", "test@example.com", "subject", "Test"));
        when(jobService.createJob(any(JobRequest.class))).thenReturn(job);

        JobRequest request = new JobRequest("email", Map.of(
                "to", "test@example.com",
                "subject", "Test"
        ));

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value("test-uuid"))
                .andExpect(jsonPath("$.status").value("QUEUED"));

        verify(jobService).createJob(any(JobRequest.class));
    }

    @Test
    void submitJob_withMissingJobType_shouldReturn400() throws Exception {
        String invalidRequest = "{\"payload\":{\"to\":\"test@example.com\"}}";

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitJob_withMissingPayload_shouldReturn400() throws Exception {
        String invalidRequest = "{\"jobType\":\"email\"}";

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getJob_whenNotFound_shouldReturn404() throws Exception {
        when(jobService.getJob("nonexistent-id")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/jobs/nonexistent-id"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getJob_whenExists_shouldReturnJobDetails() throws Exception {
        Job job = new Job("test-123", "email", Map.of("to", "user@test.com"));
        when(jobService.getJob("test-123")).thenReturn(Optional.of(job));

        mockMvc.perform(get("/api/jobs/test-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("test-123"))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }
}
