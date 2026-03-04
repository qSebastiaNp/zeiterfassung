package de.focusshift.zeiterfassung.overtime;

import de.focusshift.zeiterfassung.user.UserIdComposite;
import de.focusshift.zeiterfassung.usermanagement.UserLocalId;

import java.time.LocalDate;
import java.util.Map;

public interface OvertimeService {

    /**
     * Returns {@link OvertimeHours} for every user for the given date.
     *
     * @param date date
     * @return {@link OvertimeHours} for every user for the given date
     */
    Map<UserIdComposite, OvertimeHours> getOvertimeForDate(LocalDate date);

    /**
     * Returns {@link OvertimeHours} for the given date and user. Can be {@link OvertimeHours#ZERO}, never {@code null}.
     *
     * @param date date
     * @param userLocalId user
     * @return {@link OvertimeHours} for the date and user
     */
    OvertimeHours getOvertimeForDateAndUser(LocalDate date, UserLocalId userLocalId);
    
    /**
     * Get overtime for multiple dates for a specific user in one batch operation
     *
     * @param startDate start date (inclusive)
     * @param endDate end date (inclusive)
     * @param userLocalId user
     * @return map of dates to overtime hours
     */
    Map<LocalDate, OvertimeHours> getOvertimeForDatesAndUser(LocalDate startDate, LocalDate endDate, UserLocalId userLocalId);
}
