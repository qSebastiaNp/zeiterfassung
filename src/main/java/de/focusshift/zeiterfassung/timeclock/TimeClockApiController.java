package de.focusshift.zeiterfassung.timeclock;

import de.focusshift.zeiterfassung.tenancy.user.TenantUser;
import de.focusshift.zeiterfassung.tenancy.user.TenantUserService;
import de.focusshift.zeiterfassung.user.UserId;
import de.focusshift.zeiterfassung.user.UserIdComposite;
import de.focusshift.zeiterfassung.usermanagement.UserLocalId;
import org.slf4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

@RestController
@RequestMapping("/api/timeclock")
class TimeClockApiController {

    private static final Logger LOG = getLogger(lookup().lookupClass());

    private final TenantUserService tenantUserService;
    private final TimeClockService timeClockService;

    TimeClockApiController(TenantUserService tenantUserService, TimeClockService timeClockService) {
        this.tenantUserService = tenantUserService;
        this.timeClockService = timeClockService;
    }

    @PostMapping("/rfid")
    ResponseEntity<Map<String, Object>> handleRfid(@RequestBody RfidClockRequest request) {

        if (request.uid() == null || request.uid().isBlank()) {
            return errorResponse("UID missing");
        }

        Optional<TenantUser> maybeUser = tenantUserService.findByRfidUid(request.uid());
        if (maybeUser.isEmpty()) {
            LOG.warn("rfid uid={} not found", request.uid());
            return errorResponse("UID not found");
        }

        TenantUser tenantUser = maybeUser.get();
        if (!tenantUser.isActive()) {
            LOG.warn("rfid uid={} found but user is not active", request.uid());
            return errorResponse("UID not found");
        }

        UserId userId = new UserId(tenantUser.id());
        UserLocalId userLocalId = new UserLocalId(tenantUser.localId());
        UserIdComposite userIdComposite = new UserIdComposite(userId, userLocalId);

        Optional<TimeClock> currentClock = timeClockService.getCurrentTimeClock(userId);

        if (currentClock.isPresent()) {
            return stopClock(userIdComposite);
        } else {
            return startClock(userId);
        }
    }

    private ResponseEntity<Map<String, Object>> startClock(UserId userId) {
        timeClockService.startTimeClock(userId);
        LOG.info("time clock started for userId={}", userId.value());

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("action", "start");
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<Map<String, Object>> stopClock(UserIdComposite userIdComposite) {
        timeClockService.stopTimeClock(userIdComposite);
        LOG.info("time clock stopped for userId={}", userIdComposite.id().value());

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("action", "stop");
        return ResponseEntity.ok(body);
    }

    private static ResponseEntity<Map<String, Object>> errorResponse(String error) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("error", error);
        return ResponseEntity.ok(body);
    }

    record RfidClockRequest(String uid) {}
}
