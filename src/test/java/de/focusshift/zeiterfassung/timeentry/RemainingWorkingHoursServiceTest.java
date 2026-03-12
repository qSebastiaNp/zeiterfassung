package de.focusshift.zeiterfassung.timeentry;

import de.focusshift.zeiterfassung.usermanagement.UserLocalId;
import de.focusshift.zeiterfassung.workduration.WorkDuration;
import de.focusshift.zeiterfassung.workingtime.PlannedWorkingHours;
import de.focusshift.zeiterfassung.workingtime.WorkingTimeCalendar;
import de.focusshift.zeiterfassung.workingtime.WorkingTimeCalendarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemainingWorkingHoursServiceTest {

    private RemainingWorkingHoursService service;

    @Mock
    private WorkingTimeCalendarService workingTimeCalendarService;

    @Mock
    private TimeEntryDayService timeEntryDayService;

    @BeforeEach
    void setUp() {
        service = new RemainingWorkingHoursService(workingTimeCalendarService, timeEntryDayService);
    }

    @Test
    void calculateRemainingWorkingHours_whenNoTimeEntriesAndShouldWorkingHoursPresent() {
        // Given
        UserLocalId userLocalId = new UserLocalId(1L);
        LocalDate date = LocalDate.of(2023, 1, 1);
        LocalTime startTime = LocalTime.of(9, 0);

        ShouldWorkingHours shouldWorkingHours = new ShouldWorkingHours(Duration.ofHours(8));
        WorkingTimeCalendar workingTimeCalendar = workingTimeCalendarWithShouldHours(date, shouldWorkingHours);
        
        TimeEntryDay timeEntryDay = new TimeEntryDay(
            false, 
            date, 
            new WorkDuration(Duration.ZERO), 
            null, 
            shouldWorkingHours, 
            List.of(), 
            List.of()
        );

        when(workingTimeCalendarService.getWorkingTimeCalender(any(), any(), eq(userLocalId)))
            .thenReturn(workingTimeCalendar);
        when(timeEntryDayService.getTimeEntryDays(any(), any(), eq(userLocalId)))
            .thenReturn(List.of(timeEntryDay));

        // When
        Optional<Duration> result = service.calculateRemainingWorkingHours(userLocalId, date, startTime);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(Duration.ofHours(8));
    }

    @Test
    void calculateRemainingWorkingHours_whenSomeHoursAlreadyWorked() {
        // Given
        UserLocalId userLocalId = new UserLocalId(1L);
        LocalDate date = LocalDate.of(2023, 1, 1);
        LocalTime startTime = LocalTime.of(9, 0);

        ShouldWorkingHours shouldWorkingHours = new ShouldWorkingHours(Duration.ofHours(8));
        WorkingTimeCalendar workingTimeCalendar = workingTimeCalendarWithShouldHours(date, shouldWorkingHours);
        
        TimeEntryDay timeEntryDay = new TimeEntryDay(
            false, 
            date, 
            new WorkDuration(Duration.ofHours(4)), // 4 hours already worked
            null, 
            shouldWorkingHours, 
            List.of(), 
            List.of()
        );

        when(workingTimeCalendarService.getWorkingTimeCalender(any(), any(), eq(userLocalId)))
            .thenReturn(workingTimeCalendar);
        when(timeEntryDayService.getTimeEntryDays(any(), any(), eq(userLocalId)))
            .thenReturn(List.of(timeEntryDay));

        // When
        Optional<Duration> result = service.calculateRemainingWorkingHours(userLocalId, date, startTime);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(Duration.ofHours(4)); // 8 - 4 = 4 hours remaining
    }

    @Test
    void calculateRemainingWorkingHours_whenNoShouldWorkingHours() {
        // Given
        UserLocalId userLocalId = new UserLocalId(1L);
        LocalDate date = LocalDate.of(2023, 1, 1);
        LocalTime startTime = LocalTime.of(9, 0);

        WorkingTimeCalendar workingTimeCalendar = workingTimeCalendarWithShouldHours(date, null);

        when(workingTimeCalendarService.getWorkingTimeCalender(any(), any(), eq(userLocalId)))
            .thenReturn(workingTimeCalendar);

        // When
        Optional<Duration> result = service.calculateRemainingWorkingHours(userLocalId, date, startTime);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void calculateRemainingWorkingHours_whenAlreadyWorkedMoreThanShould() {
        // Given
        UserLocalId userLocalId = new UserLocalId(1L);
        LocalDate date = LocalDate.of(2023, 1, 1);
        LocalTime startTime = LocalTime.of(9, 0);

        ShouldWorkingHours shouldWorkingHours = new ShouldWorkingHours(Duration.ofHours(6));
        WorkingTimeCalendar workingTimeCalendar = workingTimeCalendarWithShouldHours(date, shouldWorkingHours);
        
        TimeEntryDay timeEntryDay = new TimeEntryDay(
            false, 
            date, 
            new WorkDuration(Duration.ofHours(8)), // 8 hours already worked, more than should
            null, 
            shouldWorkingHours, 
            List.of(), 
            List.of()
        );

        when(workingTimeCalendarService.getWorkingTimeCalender(any(), any(), eq(userLocalId)))
            .thenReturn(workingTimeCalendar);
        when(timeEntryDayService.getTimeEntryDays(any(), any(), eq(userLocalId)))
            .thenReturn(List.of(timeEntryDay));

        // When
        Optional<Duration> result = service.calculateRemainingWorkingHours(userLocalId, date, startTime);

        // Then
        assertThat(result).isEmpty(); // No remaining hours when already worked more than should
    }

    private WorkingTimeCalendar workingTimeCalendarWithShouldHours(LocalDate date, ShouldWorkingHours shouldWorkingHours) {
        Map<LocalDate, PlannedWorkingHours> plannedHours = new HashMap<>();
        Map<LocalDate, List<de.focusshift.zeiterfassung.absence.Absence>> absences = new HashMap<>();
        
        if (shouldWorkingHours != null) {
            plannedHours.put(date, new PlannedWorkingHours(shouldWorkingHours.duration()));
        }
        
        return new WorkingTimeCalendar(plannedHours, absences);
    }
}
