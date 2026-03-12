package com.jobqueue.worker.handler;

import com.jobqueue.worker.model.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles push notification jobs.
 * Simulates sending push notifications to mobile devices.
 */
@Component
public class NotificationJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationJobHandler.class);

    @Override
    public String getJobType() {
        return "notification";
    }

    @Override
    public String handle(Job job) throws Exception {
        String userId = (String) job.getPayload().getOrDefault("userId", "unknown");
        String title = (String) job.getPayload().getOrDefault("title", "Notification");
        String channel = (String) job.getPayload().getOrDefault("channel", "push");

        log.info("Sending notification: userId={}, title={}, channel={}", userId, title, channel);

        // Simulate notification dispatch
        Thread.sleep(500);

        log.info("Notification sent: userId={}, channel={}", userId, channel);
        return "Notification sent to " + userId + " via " + channel;
    }
}
