package io.symmetry.exercise;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "exercise_workspace_session")
public class ExerciseWorkspaceSession {

    @Id
    private UUID id;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "acceptance_id", nullable = false)
    private UUID acceptanceId;

    @Column(name = "worker_id", nullable = false, columnDefinition = "text")
    private String workerId;

    @Column(name = "expires_at", nullable = false, columnDefinition = "timestamptz")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt = Instant.now();

    protected ExerciseWorkspaceSession() {
    }

    public ExerciseWorkspaceSession(UUID id, UUID exerciseId, UUID acceptanceId, String workerId,
            Instant expiresAt) {
        this.id = id;
        this.exerciseId = exerciseId;
        this.acceptanceId = acceptanceId;
        this.workerId = workerId;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getExerciseId() {
        return exerciseId;
    }

    public UUID getAcceptanceId() {
        return acceptanceId;
    }

    public String getWorkerId() {
        return workerId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
