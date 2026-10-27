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
@Table(name = "exercise_submission")
public class ExerciseSubmission {

    @Id
    private UUID id;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "acceptance_id", nullable = false)
    private UUID acceptanceId;

    @Column(name = "worker_id", nullable = false, columnDefinition = "text")
    private String workerId;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "content_hash", nullable = false, columnDefinition = "text")
    private String contentHash;

    @Column(nullable = false, columnDefinition = "text")
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "static_findings", nullable = false, columnDefinition = "jsonb")
    private String staticFindings;

    @Column(name = "submitted_at", nullable = false, columnDefinition = "timestamptz")
    private Instant submittedAt = Instant.now();

    protected ExerciseSubmission() {
    }

    public ExerciseSubmission(UUID id, UUID exerciseId, UUID acceptanceId, String workerId,
            String content, String contentHash, String status, String staticFindings) {
        this.id = id;
        this.exerciseId = exerciseId;
        this.acceptanceId = acceptanceId;
        this.workerId = workerId;
        this.content = content;
        this.contentHash = contentHash;
        this.status = status;
        this.staticFindings = staticFindings;
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

    public String getContent() {
        return content;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getStatus() {
        return status;
    }

    public String getStaticFindings() {
        return staticFindings;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void replace(String content, String contentHash, String status, String staticFindings) {
        this.content = content;
        this.contentHash = contentHash;
        this.status = status;
        this.staticFindings = staticFindings;
        this.submittedAt = Instant.now();
    }
}
