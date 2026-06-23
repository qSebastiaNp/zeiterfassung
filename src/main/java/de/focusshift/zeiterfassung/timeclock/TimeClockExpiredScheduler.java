package de.focusshift.zeiterfassung.timeclock;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

@Component
class TimeClockExpiredScheduler {

    private static final Logger LOG = getLogger(lookup().lookupClass());

    private final TimeClockAutoStopService timeClockAutoStopService;

    TimeClockExpiredScheduler(TimeClockAutoStopService timeClockAutoStopService) {
        this.timeClockAutoStopService = timeClockAutoStopService;
    }

    @Scheduled(fixedRateString = "${zeiterfassung.timeclock.auto-stop.interval:300000}")
    @SchedulerLock(name = "autoStopExpiredTimeClocks")
    void autoStopExpiredTimeClocks() {
        LOG.debug("running auto-stop check for expired time clocks");
        timeClockAutoStopService.autoStopExpiredTimeClocks();
    }
}
