package io.symmetry.exercise;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseTestRepository extends JpaRepository<ExerciseTest, UUID> {

    List<ExerciseTest> findByExerciseIdOrderByCreatedAtAsc(UUID exerciseId);

    List<ExerciseTest> findByExerciseIdAndVisibilityOrderByCreatedAtAsc(UUID exerciseId, String visibility);

    void deleteByExerciseId(UUID exerciseId);
}
