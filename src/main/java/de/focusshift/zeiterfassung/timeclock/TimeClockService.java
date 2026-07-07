package de.focusshift.zeiterfassung.timeclock;

import de.focusshift.zeiterfassung.tenancy.user.TenantUser;
import de.focusshift.zeiterfassung.tenancy.user.TenantUserService;
import de.focusshift.zeiterfassung.timeentry.TimeEntryService;
import de.focusshift.zeiterfassung.user.UserId;
import de.focusshift.zeiterfassung.user.UserIdComposite;
import de.focusshift.zeiterfassung.user.UserSettingsProvider;
import de.focusshift.zeiterfassung.usermanagement.UserLocalId;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

@Service
public class TimeClockService {

    private static final Logger LOG = getLogger(lookup().lookupClass());

    private final TimeClockRepository timeClockRepository;
    private final TimeEntryService timeEntryService;
    private final UserSettingsProvider userSettingsProvider;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;
    private final TenantUserService tenantUserService;

    @Value("${zeiterfassung.timeclock.auto-stop.max-hours:10}")
    private int maxHours;

    TimeClockService(TimeClockRepository timeClockRepository, TimeEntryService timeEntryService,
                     UserSettingsProvider userSettingsProvider, ApplicationEventPublisher applicationEventPublisher,
                     Clock clock, TenantUserService tenantUserService) {
        this.timeClockRepository = timeClockRepository;
        this.timeEntryService = timeEntryService;
        this.userSettingsProvider = userSettingsProvider;
        this.applicationEventPublisher = applicationEventPublisher;
        this.clock = clock;
        this.tenantUserService = tenantUserService;
    }

    Optional<TimeClock> getCurrentTimeClock(UserId userId) {
        return timeClockRepository.findByOwnerAndStoppedAtIsNull(userId.value()).map(TimeClockService::toTimeClock);
    }

    void startTimeClock(UserId userId) {
        final ZonedDateTime now = ZonedDateTime.now(clock.withZone(userSettingsProvider.zoneId()));
        final TimeClock timeClock = new TimeClock(userId, now);

        timeClockRepository.save(toEntity(timeClock));

        applicationEventPublisher.publishEvent(new TimeClockStartedEvent(userId, now, timeClock.comment(), timeClock.isBreak()));
    }

    /**
     * Import a {@linkplain TimeClock} from another system.
     *
     * @param timeClock to import
     */
    public void importTimeClock(TimeClock timeClock) {
        timeClockRepository.save(toEntity(timeClock));
    }

    /**
     * Get all {@linkplain TimeClock}s for the given user.
     *
     * <p>
     * Note that {@linkplain TimeClock}s are not of interest, actually. <br />
     * A {@linkplain TimeClock} is temporary and {@linkplain TimeClockService#stopTimeClock(UserId) stopping it}
     * creates a new {@linkplain de.focusshift.zeiterfassung.timeentry.TimeEntry time entry}.
     *
     * @param userId external user id
     * @return list of all {@linkplain TimeClock}s
     */
    public List<TimeClock> findAllTimeClocks(UserId userId) {
        return timeClockRepository.findAllByOwnerOrderByIdAsc(userId.value()).stream()
            .map(TimeClockService::toTimeClock)
            .toList();
    }

    TimeClock updateTimeClock(UserId userId, TimeClockUpdate timeClockUpdate) throws TimeClockNotStartedException {

        final TimeClock timeClock = getCurrentTimeClock(userId)
            .map(existingTimeClock -> prepareTimeClockUpdate(existingTimeClock, timeClockUpdate))
            .orElseThrow(() -> new TimeClockNotStartedException(userId));

        final TimeClockEntity timeClockEntity = toEntity(timeClock);

        final TimeClock updatedTimeClock = toTimeClock(timeClockRepository.save(timeClockEntity));

        applicationEventPublisher.publishEvent(new TimeClockUpdatedEvent(userId, updatedTimeClock.startedAt(), updatedTimeClock.comment(), updatedTimeClock.isBreak()));

        return updatedTimeClock;
    }

    void stopTimeClock(UserIdComposite userIdComposite) {
        timeClockRepository.findByOwnerAndStoppedAtIsNull(userIdComposite.id().value())
            .map(entity -> timeClockEntityWithStoppedAt(entity, ZonedDateTime.now(clock.withZone(userSettingsProvider.zoneId()))))
            .map(timeClockRepository::save)
            .map(TimeClockService::toTimeClock)
            .ifPresent(timeClock -> {

                final ZonedDateTime start = timeClock.startedAt();
                final ZonedDateTime end = timeClock.stoppedAt()
                    .orElseThrow(() -> new IllegalStateException("expected stoppedAt to contain a value."));

                applicationEventPublisher.publishEvent(new TimeClockStoppedEvent(userIdComposite.id(), start, end, timeClock.comment(), timeClock.isBreak()));

                timeEntryService.createTimeEntry(userIdComposite.localId(), timeClock.comment(), start, end, timeClock.isBreak());
            });
    }

    @Transactional
    int autoStopExpiredTimeClocks() {
        final Instant cutoff = clock.instant().minus(maxHours, java.time.temporal.ChronoUnit.HOURS);
        final List<TimeClockEntity> expired = timeClockRepository.findAllExpiredBefore(cutoff);

        for (TimeClockEntity entity : expired) {
            final UserId userId = new UserId(entity.getOwner());
            final Optional<TenantUser> tenantUser = tenantUserService.findById(userId);
            if (tenantUser.isEmpty()) {
                LOG.warn("user not found for owner={}, skipping auto-stop of time clock id={}", entity.getOwner(), entity.getId());
                continue;
            }

            final ZoneId zoneId = ZoneId.of(entity.getStartedAtZoneId());
            final ZonedDateTime startedAt = ZonedDateTime.ofInstant(entity.getStartedAt(), zoneId);
            final ZonedDateTime stoppedAt = startedAt.plusHours(maxHours);
            final String comment = "SYSTEM: Automatisch nach " + maxHours + " Std. ausgestempelt.";

            final TimeClockEntity updatedEntity = TimeClockEntity.builder(entity)
                .stoppedAt(stoppedAt.toInstant())
                .stoppedAtZoneId(zoneId)
                .comment(comment)
                .build();
            timeClockRepository.save(updatedEntity);

            final UserLocalId userLocalId = new UserLocalId(tenantUser.get().localId());
            final UserIdComposite userIdComposite = new UserIdComposite(userId, userLocalId);

            applicationEventPublisher.publishEvent(new TimeClockStoppedEvent(userId, startedAt, stoppedAt, comment, false));
            timeEntryService.createTimeEntry(userLocalId, comment, startedAt, stoppedAt, false);

            LOG.info("auto-stopped time clock id={} for owner={} at {}", entity.getId(), entity.getOwner(), stoppedAt);
        }

        return expired.size();
    }

    private static TimeClockEntity toEntity(TimeClock timeClock) {
        return TimeClockEntity.builder()
            .id(timeClock.id())
            .owner(timeClock.userId().value())
            .startedAt(timeClock.startedAt().toInstant())
            .startedAtZoneId(timeClock.startedAt().getZone())
            .stoppedAt(timeClock.stoppedAt().map(ZonedDateTime::toInstant).orElse(null))
            .stoppedAtZoneId(timeClock.stoppedAt().map(ZonedDateTime::getZone).orElse(null))
            .comment(timeClock.comment())
            .isBreak(timeClock.isBreak())
            .build();
    }

    private static TimeClock toTimeClock(TimeClockEntity timeClockEntity) {
        final Long id = timeClockEntity.getId();
        final UserId userId = new UserId(timeClockEntity.getOwner());
        final ZonedDateTime startedAt = ZonedDateTime.ofInstant(timeClockEntity.getStartedAt(), ZoneId.of(timeClockEntity.getStartedAtZoneId()));
        final ZonedDateTime stoppedAt = timeClockEntity.getStoppedAt() == null ? null : ZonedDateTime.ofInstant(timeClockEntity.getStoppedAt(), ZoneId.of(timeClockEntity.getStoppedAtZoneId()));

        return new TimeClock(id, userId, startedAt, timeClockEntity.getComment(), timeClockEntity.isBreak(), Optional.ofNullable(stoppedAt));
    }

    private static TimeClock prepareTimeClockUpdate(TimeClock existingTimeClock, TimeClockUpdate timeClockUpdate) {
        return TimeClock.builder(existingTimeClock)
            .startedAt(timeClockUpdate.startedAt())
            .comment(timeClockUpdate.comment())
            .isBreak(timeClockUpdate.isBreak())
            .build();
    }

    private static TimeClockEntity timeClockEntityWithStoppedAt(TimeClockEntity entity, ZonedDateTime stoppedAt) {
        return TimeClockEntity.builder(entity)
            .stoppedAt(stoppedAt.toInstant())
            .stoppedAtZoneId(stoppedAt.getZone())
            .build();
    }
}
