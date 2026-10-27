package io.symmetry.exercise;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "exercise_acceptance")
public class ExerciseAcceptance {

    @Id
    private UUID id;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "worker_id", nullable = false, columnDefinition = "text")
    private String workerId;

    @Column(name = "worker_public_key", nullable = false, columnDefinition = "text")
    private String workerPublicKey;

    @Column(name = "task_document_hash", nullable = false, columnDefinition = "text")
    private String taskDocumentHash;

    @Column(nullable = false, columnDefinition = "text")
    private String signature;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String agreements;

    @Column(nullable = false)
    private boolean verified;

    @Column(nullable = false, columnDefinition = "text")
    private String status = "ACTIVE";

    @Column(name = "accepted_at", nullable = false, columnDefinition = "timestamptz")
    private Instant acceptedAt = Instant.now();

    @Column(name = "deadline_at", nullable = false, columnDefinition = "timestamptz")
    private Instant deadlineAt;

    protected ExerciseAcceptance() {
    }

    public ExerciseAcceptance(UUID id, UUID exerciseId, String workerId, String workerPublicKey,
            String taskDocumentHash, String signature, String agreements, boolean verified,
            Instant acceptedAt, Instant deadlineAt) {
        this.id = id;
        this.exerciseId = exerciseId;
        this.workerId = workerId;
        this.workerPublicKey = workerPublicKey;
        this.taskDocumentHash = taskDocumentHash;
        this.signature = signature;
        this.agreements = agreements;
        this.verified = verified;
        this.acceptedAt = acceptedAt;
        this.deadlineAt = deadlineAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getExerciseId() {
        return exerciseId;
    }

    public String getWorkerId() {
        return workerId;
    }

    public String getWorkerPublicKey() {
        return workerPublicKey;
    }

    public String getTaskDocumentHash() {
        return taskDocumentHash;
    }

    public String getSignature() {
        return signature;
    }

    public String getAgreements() {
        return agreements;
    }

    public boolean isVerified() {
        return verified;
    }

    public String getStatus() {
        return status;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public Instant getDeadlineAt() {
        return deadlineAt;
    }

    public boolean isActiveAt(Instant now) {
        return "ACTIVE".equals(status) && deadlineAt.isAfter(now);
    }

    public void expire() {
        this.status = "EXPIRED";
    }

    public void markSubmitted() {
        this.status = "SUBMITTED";
    }
}
