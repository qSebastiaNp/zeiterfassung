package de.focusshift.zeiterfassung.timeentry;

import de.focusshift.zeiterfassung.usermanagement.UserLocalId;
import de.focusshift.zeiterfassung.workingtime.WorkingTimeCalendarService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/**
 * Service to calculate remaining working hours for a given day and start time.
 */
@Service
public class RemainingWorkingHoursService {

    private final WorkingTimeCalendarService workingTimeCalendarService;
    private final TimeEntryDayService timeEntryDayService;

    public RemainingWorkingHoursService(WorkingTimeCalendarService workingTimeCalendarService, TimeEntryDayService timeEntryDayService) {
        this.workingTimeCalendarService = workingTimeCalendarService;
        this.timeEntryDayService = timeEntryDayService;
    }

    /**
     * Calculates the remaining working hours for a given user, date, and start time.
     * This represents the time from the start time until the target working hours for the day are reached.
     *
     * @param userLocalId the user to calculate for
     * @param date the date to calculate for
     * @param startTime the start time of the new time entry
     * @return the remaining working hours as Duration, empty if calculation is not possible
     */
    public Optional<Duration> calculateRemainingWorkingHours(UserLocalId userLocalId, LocalDate date, LocalTime startTime) {
        try {
            // Get the should working hours for the day
            var shouldWorkingHours = workingTimeCalendarService.getWorkingTimeCalender(date, date.plusDays(1), userLocalId)
                .shouldWorkingHours(date);

            if (shouldWorkingHours.isEmpty() || shouldWorkingHours.get().duration().isZero()) {
                return Optional.empty();
            }

            // Get existing time entries for the day to calculate already worked hours
            var timeEntryDays = timeEntryDayService.getTimeEntryDays(date, date.plusDays(1), userLocalId);
            var timeEntryDay = timeEntryDays.stream()
                .filter(day -> day.date().equals(date))
                .findFirst();

            Duration alreadyWorked = Duration.ZERO;
            if (timeEntryDay.isPresent()) {
                alreadyWorked = timeEntryDay.get().workDuration().durationInMinutes();
            }

            // Calculate remaining hours: shouldHours - alreadyWorked
            Duration remainingShouldHours = shouldWorkingHours.get().duration().minus(alreadyWorked);
            
            // If no remaining hours or negative, return empty
            if (remainingShouldHours.isNegative() || remainingShouldHours.isZero()) {
                return Optional.empty();
            }

            return Optional.of(remainingShouldHours);
        } catch (Exception e) {
            // Log error and return empty to avoid breaking the UI
            return Optional.empty();
        }
    }
}
