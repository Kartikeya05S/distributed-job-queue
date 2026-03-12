package com.jobqueue.worker.handler;

import com.jobqueue.worker.model.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles report generation jobs.
 * Simulates report generation with processing delay.
 */
@Component
public class ReportJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(ReportJobHandler.class);

    @Override
    public String getJobType() {
        return "report";
    }

    @Override
    public String handle(Job job) throws Exception {
        String reportType = (String) job.getPayload().getOrDefault("type", "summary");
        String format = (String) job.getPayload().getOrDefault("format", "pdf");

        log.info("Generating report: type={}, format={}", reportType, format);

        // Simulate report generation
        Thread.sleep(3000);

        log.info("Report generated successfully: type={}", reportType);
        return "Report generated: " + reportType + "." + format;
    }
}
