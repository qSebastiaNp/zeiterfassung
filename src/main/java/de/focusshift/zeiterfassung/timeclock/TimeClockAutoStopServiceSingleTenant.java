package de.focusshift.zeiterfassung.timeclock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import static de.focusshift.zeiterfassung.tenancy.TenantConfigurationProperties.SINGLE;

@Service
@ConditionalOnProperty(value = "zeiterfassung.tenant.mode", havingValue = SINGLE, matchIfMissing = true)
class TimeClockAutoStopServiceSingleTenant implements TimeClockAutoStopService {

    private final TimeClockService timeClockService;

    TimeClockAutoStopServiceSingleTenant(TimeClockService timeClockService) {
        this.timeClockService = timeClockService;
    }

    @Override
    public void autoStopExpiredTimeClocks() {
        timeClockService.autoStopExpiredTimeClocks();
    }
}
