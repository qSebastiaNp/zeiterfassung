package de.focusshift.zeiterfassung.timeentry;

import de.focusshift.zeiterfassung.security.CurrentUser;
import de.focusshift.zeiterfassung.security.oidc.CurrentOidcUser;
import de.focusshift.zeiterfassung.usermanagement.UserLocalId;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;
import static org.springframework.format.annotation.DateTimeFormat.ISO.TIME;

/**
 * REST controller to provide remaining working hours information for time entry auto-fill functionality.
 */
@RestController
@RequestMapping("/api/timeentries")
public class RemainingWorkingHoursController {

    private final RemainingWorkingHoursService remainingWorkingHoursService;

    public RemainingWorkingHoursController(RemainingWorkingHoursService remainingWorkingHoursService) {
        this.remainingWorkingHoursService = remainingWorkingHoursService;
    }

    /**
     * Calculates the remaining working hours for a given date and start time.
     * This endpoint is used to auto-fill the duration field when only start time is provided.
     *
     * @param date the date of the time entry
     * @param startTime the start time of the time entry
     * @param userLocalId the user ID (optional, defaults to current user)
     * @param currentUser the current authenticated user
     * @return a map containing the remaining hours in "HH:mm" format, or empty if not available
     */
    @GetMapping("/remaining-hours")
    public Map<String, String> getRemainingWorkingHours(
            @RequestParam @DateTimeFormat(iso = DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = TIME) LocalTime startTime,
            @RequestParam(required = false) Long userLocalId,
            @CurrentUser CurrentOidcUser currentUser) {

        // Always use current user for now - this is a convenience feature
        UserLocalId targetUserLocalId = currentUser.getUserIdComposite().localId();

        // Calculate remaining working hours
        var remainingHours = remainingWorkingHoursService.calculateRemainingWorkingHours(targetUserLocalId, date, startTime);

        Map<String, String> response = new HashMap<>();
        remainingHours.ifPresent(duration -> {
            String formattedDuration = String.format("%02d:%02d", 
                Math.abs(duration.toHours()), 
                Math.abs(duration.toMinutes() % 60));
            response.put("remainingHours", formattedDuration);
        });

        return response;
    }
}
