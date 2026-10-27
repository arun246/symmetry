package io.symmetry.exercise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseSubmissionRepository extends JpaRepository<ExerciseSubmission, UUID> {

    Optional<ExerciseSubmission> findByAcceptanceId(UUID acceptanceId);

    List<ExerciseSubmission> findByExerciseIdOrderBySubmittedAtDesc(UUID exerciseId);

    List<ExerciseSubmission> findByWorkerIdOrderBySubmittedAtDesc(String workerId);
}
