package io.symmetry.exercise;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseSourceRepository extends JpaRepository<ExerciseSource, UUID> {

    Optional<ExerciseSource> findByExerciseId(UUID exerciseId);

    void deleteByExerciseId(UUID exerciseId);
}
