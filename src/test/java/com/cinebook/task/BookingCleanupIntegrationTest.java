package com.cinebook.task;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.ScheduledTask;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BookingCleanupIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private BookingCleanupTask bookingCleanupTask;

    @Autowired
    private ScheduledAnnotationBeanPostProcessor scheduledAnnotationBeanPostProcessor;

    @Test
    void schedulingInfrastructure_IsActiveAndCleanupTaskRegistered() {
        // 1. Verify scheduling bean post processor is in context (@EnableScheduling active)
        assertNotNull(scheduledAnnotationBeanPostProcessor, "ScheduledAnnotationBeanPostProcessor must be present");

        // 2. Verify BookingCleanupTask bean exists
        assertNotNull(bookingCleanupTask, "BookingCleanupTask bean must be registered");
        assertTrue(applicationContext.containsBean("bookingCleanupTask"));

        // 3. Verify Scheduled Tasks include the cleanup task
        Set<ScheduledTask> scheduledTasks = scheduledAnnotationBeanPostProcessor.getScheduledTasks();
        assertFalse(scheduledTasks.isEmpty(), "Scheduled tasks set must not be empty");

        boolean cleanupTaskFound = scheduledTasks.stream()
                .anyMatch(task -> task.getTask().toString().contains("cleanupExpiredBookingsAndHolds")
                        || task.toString().contains("cleanupExpiredBookingsAndHolds"));
        assertTrue(cleanupTaskFound, "cleanupExpiredBookingsAndHolds method must be registered with the Spring scheduler");

        // 4. Verify cleanup task can be executed cleanly within Spring context
        assertDoesNotThrow(() -> bookingCleanupTask.cleanupExpiredBookingsAndHolds(),
                "Manual execution of cleanupExpiredBookingsAndHolds must succeed without unhandled exceptions");
    }
}
