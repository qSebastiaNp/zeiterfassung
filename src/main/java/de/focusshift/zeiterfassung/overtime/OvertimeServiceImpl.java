package de.focusshift.zeiterfassung.overtime;

import de.focusshift.zeiterfassung.report.ReportDay;
import de.focusshift.zeiterfassung.report.ReportServiceRaw;
import de.focusshift.zeiterfassung.user.UserIdComposite;
import de.focusshift.zeiterfassung.usermanagement.OvertimeAccount;
import de.focusshift.zeiterfassung.usermanagement.OvertimeAccountService;
import de.focusshift.zeiterfassung.usermanagement.UserLocalId;
import de.focusshift.zeiterfassung.workingtime.WorkingTime;
import de.focusshift.zeiterfassung.workingtime.WorkingTimeService;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

@Service
class OvertimeServiceImpl implements OvertimeService {

    private final ReportServiceRaw reportServiceRaw;
    private final OvertimeAccountService overtimeAccountService;
    private final WorkingTimeService workingTimeService;

    OvertimeServiceImpl(ReportServiceRaw reportServiceRaw, OvertimeAccountService overtimeAccountService, WorkingTimeService workingTimeService) {
        this.reportServiceRaw = reportServiceRaw;
        this.overtimeAccountService = overtimeAccountService;
        this.workingTimeService = workingTimeService;
    }

    @Override
    public Map<UserIdComposite, OvertimeHours> getOvertimeForDate(LocalDate date) {

        final ReportDay reportDay = reportServiceRaw.getReportDayForAllUsers(date);

        return reportDay.overtimeByUser().entrySet().stream().collect(
            toMap(
                Map.Entry::getKey,
                entry -> new OvertimeHours(entry.getValue().duration())
            )
        );
    }

    @Override
    public OvertimeHours getOvertimeForDateAndUser(LocalDate date, UserLocalId userLocalId) {
        OvertimeHours overtime = getOvertimeForDate(date).entrySet().stream()
            .filter(entry -> entry.getKey().localId().equals(userLocalId))
            .findFirst()
            .map(Map.Entry::getValue)
            .orElseThrow(() -> new IllegalStateException("expected OvertimeHours to exist for %s".formatted(userLocalId)));

        final List<WorkingTime> workingTimes = workingTimeService.getAllWorkingTimesByUser(userLocalId);
        final OvertimeAccount overtimeAccount = overtimeAccountService.getOvertimeAccount(userLocalId);
        
        return applyOvertimeCap(overtime, date, userLocalId, workingTimes, overtimeAccount);
    }
    
    @Override
    public Map<LocalDate, OvertimeHours> getOvertimeForDatesAndUser(LocalDate startDate, LocalDate endDate, UserLocalId userLocalId) {
        // Cache working times and overtime account for performance
        final List<WorkingTime> workingTimes = workingTimeService.getAllWorkingTimesByUser(userLocalId);
        final OvertimeAccount overtimeAccount = overtimeAccountService.getOvertimeAccount(userLocalId);
        
        Map<LocalDate, OvertimeHours> result = new HashMap<>();
        
        // OPTIMIZED: Use batch processing instead of day-by-day queries
        // This reduces thousands of queries to just a few batch queries
        final Map<LocalDate, ReportDay> reportDays = reportServiceRaw.getReportDaysForAllUsers(startDate, endDate);
        
        for (Map.Entry<LocalDate, ReportDay> entry : reportDays.entrySet()) {
            final LocalDate date = entry.getKey();
            final ReportDay reportDay = entry.getValue();
            
            final OvertimeHours overtime = reportDay.overtimeByUser().entrySet().stream()
                .filter(userEntry -> userEntry.getKey().localId().equals(userLocalId))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse(OvertimeHours.ZERO);
            
            result.put(date, applyOvertimeCap(overtime, date, userLocalId, workingTimes, overtimeAccount));
        }
        
        return result;
    }

    /**
     * Applies overtime cap if user is not allowed to work overtime.
     * If overtime is not allowed, the worked hours will be capped at the planned working hours for that day.
     * 
     * @param overtime the calculated overtime hours
     * @param date the date for which to check working time
     * @param userLocalId the user to check overtime permissions for
     * @param workingTimes cached list of working times for the user
     * @param overtimeAccount cached overtime account for the user
     * @return capped overtime hours if overtime is not allowed, otherwise original overtime hours
     */
    private OvertimeHours applyOvertimeCap(OvertimeHours overtime, LocalDate date, UserLocalId userLocalId, 
                                          List<WorkingTime> workingTimes, OvertimeAccount overtimeAccount) {
        
        // If overtime is allowed, return original overtime
        if (overtimeAccount.isAllowed()) {
            return overtime;
        }
        
        // Find appropriate working time for the date
        final WorkingTime workingTime = workingTimes.stream()
            .filter(wt -> wt.validFrom().isEmpty() || !date.isBefore(wt.validFrom().get()))
            .filter(wt -> wt.validTo().isEmpty() || !date.isAfter(wt.validTo().get()))
            .findFirst()
            .orElse(null);
        
        if (workingTime != null) {
            final Duration plannedHours = workingTime.getForDayOfWeek(date.getDayOfWeek()).duration();
            
            // Calculate worked hours from overtime (overtime = worked - planned)
            // So worked = overtime + planned
            final Duration workedHours = overtime.duration().plus(plannedHours);
            
            // Cap worked hours at planned hours (no overtime allowed)
            if (workedHours.compareTo(plannedHours) > 0) {
                return OvertimeHours.ZERO;
            }
        }
        
        return overtime;
    }
}
