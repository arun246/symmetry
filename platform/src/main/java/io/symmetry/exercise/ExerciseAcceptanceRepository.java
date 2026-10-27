package io.symmetry.exercise;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExerciseAcceptanceRepository extends JpaRepository<ExerciseAcceptance, UUID> {

    Optional<ExerciseAcceptance> findByExerciseIdAndWorkerId(UUID exerciseId, String workerId);

    Optional<ExerciseAcceptance> findByIdAndWorkerId(UUID id, String workerId);

    List<ExerciseAcceptance> findByWorkerIdOrderByAcceptedAtDesc(String workerId);

    @Modifying
    @Query("""
            update ExerciseAcceptance a
               set a.status = 'EXPIRED'
             where a.status = 'ACTIVE'
               and a.deadlineAt <= :now
            """)
    int expireStale(@Param("now") Instant now);
}
