package de.focusshift.zeiterfassung.timeclock;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface TimeClockRepository extends CrudRepository<TimeClockEntity, Long> {

    Optional<TimeClockEntity> findByOwnerAndStoppedAtIsNull(String owner);

    List<TimeClockEntity> findAllByOwnerOrderByIdAsc(String owner);

    @Query("SELECT t FROM TimeClockEntity t WHERE t.stoppedAt IS NULL AND t.startedAt < :cutoff")
    List<TimeClockEntity> findAllExpiredBefore(@Param("cutoff") Instant cutoff);
}
