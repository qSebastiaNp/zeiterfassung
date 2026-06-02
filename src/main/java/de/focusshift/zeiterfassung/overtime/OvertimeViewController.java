package de.focusshift.zeiterfassung.overtime;

import de.focus_shift.launchpad.api.HasLaunchpad;
import de.focusshift.zeiterfassung.security.AuthenticationFacade;
import de.focusshift.zeiterfassung.timeclock.HasTimeClock;
import de.focusshift.zeiterfassung.timeentry.TimeEntry;
import de.focusshift.zeiterfassung.timeentry.TimeEntryService;
import de.focusshift.zeiterfassung.user.DateFormatter;
import de.focusshift.zeiterfassung.usermanagement.User;
import de.focusshift.zeiterfassung.usermanagement.UserLocalId;
import de.focusshift.zeiterfassung.usermanagement.UserManagementService;
import de.focusshift.zeiterfassung.web.DurationFormatter;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.LocaleResolver;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping("/overtime")
public class OvertimeViewController implements HasLaunchpad, HasTimeClock {

    private final OvertimeService overtimeService;
    private final UserManagementService userManagementService;
    private final AuthenticationFacade authenticationFacade;
    private final MessageSource messageSource;
    private final LocaleResolver localeResolver;
    private final TimeEntryService timeEntryService;
    private final DateFormatter dateFormatter;

    public OvertimeViewController(OvertimeService overtimeService, UserManagementService userManagementService, 
                                AuthenticationFacade authenticationFacade, MessageSource messageSource,
                                LocaleResolver localeResolver, TimeEntryService timeEntryService, DateFormatter dateFormatter) {
        this.overtimeService = overtimeService;
        this.userManagementService = userManagementService;
        this.authenticationFacade = authenticationFacade;
        this.messageSource = messageSource;
        this.localeResolver = localeResolver;
        this.timeEntryService = timeEntryService;
        this.dateFormatter = dateFormatter;
    }

    @GetMapping
    public String viewOvertime(Model model) {
        final var userIdComposite = authenticationFacade.getCurrentUserIdComposite();
        final Optional<User> userOptional = userManagementService.findUserByLocalId(userIdComposite.localId());
        
        if (userOptional.isEmpty()) {
            return "redirect:/login";
        }

        final int currentYear = LocalDate.now().getYear();
        
        return "redirect:/overtime/year/" + currentYear;
    }
    
    @GetMapping("/year/{year}/month/{month}")
    public String viewOvertimeMonth(@PathVariable int year, @PathVariable int month, Model model, HttpServletRequest request) {
        final var userIdComposite = authenticationFacade.getCurrentUserIdComposite();
        final Optional<User> userOptional = userManagementService.findUserByLocalId(userIdComposite.localId());
        
        if (userOptional.isEmpty()) {
            return "redirect:/login";
        }

        final User user = userOptional.get();
        final UserLocalId userLocalId = userIdComposite.localId();
        final LocalDate today = LocalDate.now();
        final YearMonth yearMonth = YearMonth.of(year, month);
        final boolean isCurrentMonth = yearMonth.equals(YearMonth.from(today));
        
        final LocalDate monthStart = yearMonth.atDay(1);
        final LocalDate monthEnd = yearMonth.atEndOfMonth();
        final LocalDate actualMonthEnd = isCurrentMonth ? today : monthEnd;
        
        final Map<LocalDate, OvertimeHours> overtimeByDate = overtimeService.getOvertimeForDatesAndUser(monthStart, actualMonthEnd, userLocalId);
        
        OvertimeHours monthTotal = OvertimeHours.ZERO;
        final List<DailyOvertimeDto> dailyData = new ArrayList<>();
        
        for (LocalDate date = actualMonthEnd; !date.isBefore(monthStart); date = date.minusDays(1)) {
            final OvertimeHours dailyOvertime = overtimeByDate.getOrDefault(date, OvertimeHours.ZERO);
            final boolean isToday = date.equals(today);
            
            if (!isToday) {
                monthTotal = monthTotal.plus(dailyOvertime);
            }
            
            final List<TimeEntry> dayEntries = timeEntryService.getEntries(date, date.plusDays(1), userLocalId);
            final String timeEntriesCompact = dayEntries.stream()
                .filter(entry -> !entry.isBreak())
                .filter(entry -> entry.start() != null && entry.end() != null)
                .sorted((e1, e2) -> e1.start().compareTo(e2.start()))
                .map(entry -> String.format("%s - %s", entry.start().toLocalTime(), entry.end().toLocalTime()))
                .collect(java.util.stream.Collectors.joining(", "));
            
            final boolean hasNoWorkTime = !isToday && dailyOvertime.equals(OvertimeHours.ZERO) && timeEntriesCompact.isEmpty();
            
            dailyData.add(new DailyOvertimeDto(
                date,
                dailyOvertime,
                DurationFormatter.toDurationString(dailyOvertime.duration(), messageSource, localeResolver.resolveLocale(request)),
                dailyOvertime.isNegative(),
                timeEntriesCompact,
                hasNoWorkTime
            ));
        }
        
        final YearMonth previousMonth = yearMonth.minusMonths(1);
        final YearMonth nextMonth = yearMonth.plusMonths(1);
        final boolean canNavigateToNext = nextMonth.isBefore(YearMonth.from(today));
        
        model.addAttribute("year", year);
        model.addAttribute("month", month);
        model.addAttribute("monthLabel", dateFormatter.formatYearMonth(yearMonth));
        model.addAttribute("previousYear", previousMonth.getYear());
        model.addAttribute("previousMonth", previousMonth.getMonthValue());
        model.addAttribute("nextYear", nextMonth.getYear());
        model.addAttribute("nextMonth", nextMonth.getMonthValue());
        model.addAttribute("canNavigateToNext", canNavigateToNext);
        model.addAttribute("dailyData", dailyData);
        model.addAttribute("monthTotal", monthTotal);
        model.addAttribute("monthTotalFormatted", DurationFormatter.toDurationString(monthTotal.duration(), messageSource, localeResolver.resolveLocale(request)));
        model.addAttribute("monthTotalNegative", monthTotal.isNegative());
        model.addAttribute("user", user);
        
        return "overtime/overtime-month-view";
    }

    @GetMapping("/year/{year}")
    public String viewOvertimeYear(@PathVariable int year, Model model, HttpServletRequest request) {
        final var userIdComposite = authenticationFacade.getCurrentUserIdComposite();
        final Optional<User> userOptional = userManagementService.findUserByLocalId(userIdComposite.localId());
        
        if (userOptional.isEmpty()) {
            return "redirect:/login";
        }

        final User user = userOptional.get();
        final UserLocalId userLocalId = userIdComposite.localId();
        final boolean isCurrentYear = year == LocalDate.now().getYear();
        
        // Get overtime data for the entire year
        final LocalDate startDate = LocalDate.of(year, 1, 1);
        final LocalDate endDate = LocalDate.of(year, 12, 31);
        final LocalDate today = LocalDate.now();
        
        // Optimize data retrieval: only query months that have data or are in the past
        final LocalDate actualEndDate;
        final Map<LocalDate, OvertimeHours> overtimeByDate;
        
        if (isCurrentYear) {
            // Current year: only query up to today
            actualEndDate = today.isBefore(endDate) ? today : endDate;
            overtimeByDate = overtimeService.getOvertimeForDatesAndUser(startDate, actualEndDate, userLocalId);
        } else {
            // Past years: query full year - the batch operation is already efficient
            actualEndDate = endDate;
            overtimeByDate = overtimeService.getOvertimeForDatesAndUser(startDate, actualEndDate, userLocalId);
        }
        
        // Group by month and calculate monthly totals
        final List<MonthlyOvertimeDto> monthlyData = new ArrayList<>();
        OvertimeHours yearTotal = OvertimeHours.ZERO;
        
        // Determine current month for prioritization
        final int currentMonthValue = LocalDate.now().getMonthValue();
        
        // Create list of months in desired order
        final List<Integer> monthOrder = new ArrayList<>();
        
        if (isCurrentYear) {
            // Current year: current month first, then previous months in reverse order
            monthOrder.add(currentMonthValue); // Current month first
            // Add previous months in reverse order (current-1 -> 1)
            for (int month = currentMonthValue - 1; month >= 1; month--) {
                monthOrder.add(month);
            }
            // No future months in current year
        } else {
            // Past years: all months in reverse order (12 -> 1)
            for (int month = 12; month >= 1; month--) {
                monthOrder.add(month);
            }
        }
        
        // Lazy loading: only load data for months that will be displayed
        for (int month : monthOrder) {
            final YearMonth yearMonth = YearMonth.of(year, month);
            final LocalDate monthStart = yearMonth.atDay(1);
            final LocalDate monthEnd = yearMonth.atEndOfMonth();
            
            OvertimeHours monthTotal = OvertimeHours.ZERO;
            final List<DailyOvertimeDto> dailyData = new ArrayList<>();
            
            // Only process months that are in the past or current (future months are not in monthOrder)
            if (!monthStart.isAfter(today)) {
                // For current month: only show days up to today
                LocalDate actualMonthEnd = (isCurrentYear && month == currentMonthValue) ? today : monthEnd;
                
                // Process days in reverse order (31 → 1)
                for (LocalDate date = actualMonthEnd; !date.isBefore(monthStart); date = date.minusDays(1)) {
                    final OvertimeHours dailyOvertime;
                    
                    // Use batch data for past/current days, future days are 0
                    if (!date.isAfter(today)) {
                        dailyOvertime = overtimeByDate.getOrDefault(date, OvertimeHours.ZERO);
                        
                        // Today is always shown in parentheses and never included in monthly total
                        boolean isToday = date.equals(today);
                        
                        // Only add to month total if not today
                        if (!isToday) {
                            monthTotal = monthTotal.plus(dailyOvertime);
                        }
                        
                        // Get time entries for this day
                        final List<TimeEntry> dayEntries = timeEntryService.getEntries(date, date.plusDays(1), userLocalId);
                        final String timeEntriesCompact = dayEntries.stream()
                            .filter(entry -> !entry.isBreak())
                            .filter(entry -> entry.start() != null && entry.end() != null)
                            .sorted((e1, e2) -> e1.start().compareTo(e2.start()))
                            .map(entry -> String.format("%s - %s", entry.start().toLocalTime(), entry.end().toLocalTime()))
                            .collect(java.util.stream.Collectors.joining(", "));
                        
                        final boolean hasNoWorkTime = !isToday && dailyOvertime.equals(OvertimeHours.ZERO) && timeEntriesCompact.isEmpty();
                        
                        dailyData.add(new DailyOvertimeDto(
                            date,
                            dailyOvertime,
                            DurationFormatter.toDurationString(dailyOvertime.duration(), messageSource, localeResolver.resolveLocale(request)),
                            dailyOvertime.isNegative(),
                            timeEntriesCompact,
                            hasNoWorkTime
                        ));
                    } else {
                        dailyOvertime = OvertimeHours.ZERO;
                        dailyData.add(new DailyOvertimeDto(
                            date,
                            dailyOvertime,
                            DurationFormatter.toDurationString(dailyOvertime.duration(), messageSource, localeResolver.resolveLocale(request)),
                            dailyOvertime.isNegative(),
                            "",
                            false
                        ));
                    }
                }
            }
            // Future months are completely skipped (no else block needed)
            
            yearTotal = yearTotal.plus(monthTotal);
            monthlyData.add(new MonthlyOvertimeDto(
                yearMonth,
                monthTotal,
                DurationFormatter.toDurationString(monthTotal.duration(), messageSource, localeResolver.resolveLocale(request)),
                monthTotal.isNegative(),
                dailyData
            ));
        }
        
        // Prepare navigation for previous/next years
        final int previousYear = year - 1;
        final int nextYear = year + 1;
        final boolean canNavigateToNext = year < LocalDate.now().getYear();
        
        model.addAttribute("year", year);
        model.addAttribute("previousYear", previousYear);
        model.addAttribute("nextYear", nextYear);
        model.addAttribute("canNavigateToNext", canNavigateToNext);
        model.addAttribute("monthlyData", monthlyData);
        model.addAttribute("yearTotal", yearTotal);
        model.addAttribute("yearTotalFormatted", DurationFormatter.toDurationString(yearTotal.duration(), messageSource, localeResolver.resolveLocale(request)));
        model.addAttribute("yearTotalNegative", yearTotal.isNegative());
        model.addAttribute("user", user);
        
        // Create month options for dropdown
        final List<MonthOptionDto> monthOptions = new ArrayList<>();
        for (int m = currentMonthValue; m >= 1; m--) {
            final YearMonth yearMonth = YearMonth.of(year, m);
            final String label = dateFormatter.formatYearMonth(yearMonth);
            final String url = String.format("/overtime/year/%d/month/%d", year, m);
            monthOptions.add(new MonthOptionDto(url, label));
        }
        model.addAttribute("monthOptions", monthOptions);
        
        return "overtime/overtime-year-view";
    }
    
    public record MonthOptionDto(
        String url,
        String label
    ) {}
    
    public record DailyOvertimeDto(
        LocalDate date,
        OvertimeHours overtime,
        String overtimeFormatted,
        boolean isNegative,
        String timeEntriesCompact,
        boolean hasNoWorkTime
    ) {}
    
    public record MonthlyOvertimeDto(
        YearMonth month,
        OvertimeHours monthTotal,
        String monthTotalFormatted,
        boolean isNegative,
        List<DailyOvertimeDto> dailyData
    ) {}
}
