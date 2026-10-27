package io.symmetry.exercise;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseSideCaseRepository extends JpaRepository<ExerciseSideCase, UUID> {

    List<ExerciseSideCase> findByExerciseIdOrderByCreatedAtAsc(UUID exerciseId);

    List<ExerciseSideCase> findByExerciseIdAndVisibilityOrderByCreatedAtAsc(UUID exerciseId, String visibility);

    void deleteByExerciseId(UUID exerciseId);
}
