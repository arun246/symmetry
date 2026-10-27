package io.symmetry.exercise;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseWorkspaceSessionRepository extends JpaRepository<ExerciseWorkspaceSession, UUID> {

    Optional<ExerciseWorkspaceSession> findByAcceptanceId(UUID acceptanceId);
}
