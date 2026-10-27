package io.symmetry.exercise;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseStubRepository extends JpaRepository<ExerciseStub, UUID> {

    List<ExerciseStub> findByExerciseIdOrderByStartLineAsc(UUID exerciseId);

    void deleteByExerciseId(UUID exerciseId);
}
