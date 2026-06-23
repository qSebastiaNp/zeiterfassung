package de.focusshift.zeiterfassung.timeclock;

import de.focusshift.zeiterfassung.tenancy.tenant.TenantContextRunner;
import org.slf4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import static de.focusshift.zeiterfassung.tenancy.TenantConfigurationProperties.MULTI;
import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

@Service
@ConditionalOnProperty(value = "zeiterfassung.tenant.mode", havingValue = MULTI)
class TimeClockAutoStopServiceMultiTenant implements TimeClockAutoStopService {

    private static final Logger LOG = getLogger(lookup().lookupClass());

    private final TenantContextRunner tenantContextRunner;
    private final TimeClockService timeClockService;

    TimeClockAutoStopServiceMultiTenant(TenantContextRunner tenantContextRunner, TimeClockService timeClockService) {
        this.tenantContextRunner = tenantContextRunner;
        this.timeClockService = timeClockService;
    }

    @Override
    public void autoStopExpiredTimeClocks() {
        tenantContextRunner.runForEachActiveTenant(() -> {
            final int count = timeClockService.autoStopExpiredTimeClocks();
            if (count > 0) {
                LOG.info("auto-stopped {} expired time clocks", count);
            }
        }).run();
    }
}
