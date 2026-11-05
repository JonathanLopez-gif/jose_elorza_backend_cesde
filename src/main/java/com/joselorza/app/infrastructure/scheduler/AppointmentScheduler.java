package com.joselorza.app.infrastructure.scheduler;

import com.joselorza.app.application.service.AppointmentJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Disparadores de los jobs de agenda (las expresiones cron son configurables por entorno).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentScheduler {

    private final AppointmentJobService jobService;

    @Scheduled(cron = "${app.scheduling.reminder-cron}")
    public void sendReminders() {
        int processed = jobService.sendReminders();
        if (processed > 0) {
            log.info("Reminder job: {} reminders sent", processed);
        }
    }

    @Scheduled(cron = "${app.scheduling.no-show-cron}")
    public void markNoShows() {
        int processed = jobService.markNoShows();
        if (processed > 0) {
            log.info("NO_SHOW job: {} appointments marked", processed);
        }
    }

    @Scheduled(cron = "${app.scheduling.payment-expiration-cron}")
    public void expireOverduePayments() {
        int processed = jobService.expireOverduePayments();
        if (processed > 0) {
            log.info("Payment expiration job: {} appointments cancelled", processed);
        }
    }
}
