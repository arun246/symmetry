package io.symmetry.exercise;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseRepository extends JpaRepository<Exercise, UUID> {

    Page<Exercise> findByStatus(String status, Pageable pageable);

    Page<Exercise> findByOrgIdOrderByCreatedAtDesc(UUID orgId, Pageable pageable);

    List<Exercise> findByOrgIdAndStatusOrderByCreatedAtDesc(UUID orgId, String status);
}
