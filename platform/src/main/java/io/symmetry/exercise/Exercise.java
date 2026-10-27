package io.symmetry.exercise;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "exercise")
public class Exercise {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Column(nullable = false, columnDefinition = "text")
    private String language;

    @Column(nullable = false, columnDefinition = "text")
    private String status = "DRAFT";

    @Column(nullable = false)
    private int version = 1;

    @Column(nullable = false, columnDefinition = "text")
    private String complexity = "MEDIUM";

    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    @Column(nullable = false, columnDefinition = "text")
    private String currency = "USD";

    @Column(name = "deadline_days", nullable = false)
    private int deadlineDays = 7;

    @Column(name = "task_document", nullable = false, columnDefinition = "text")
    private String taskDocument = "";

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt = Instant.now();

    @Column(name = "published_at", columnDefinition = "timestamptz")
    private Instant publishedAt;

    @Column(name = "parent_exercise_id")
    private UUID parentExerciseId;

    protected Exercise() {
    }

    public Exercise(UUID id, UUID orgId, UUID createdBy, String title, String summary,
            String language, String complexity, long priceCents, String currency,
            int deadlineDays, String taskDocument) {
        this(id, orgId, createdBy, title, summary, language, complexity, priceCents, currency,
                deadlineDays, taskDocument, null);
    }

    public Exercise(UUID id, UUID orgId, UUID createdBy, String title, String summary,
            String language, String complexity, long priceCents, String currency,
            int deadlineDays, String taskDocument, UUID parentExerciseId) {
        this.id = id;
        this.orgId = orgId;
        this.createdBy = createdBy;
        this.title = title;
        this.summary = summary;
        this.language = language;
        this.complexity = complexity;
        this.priceCents = priceCents;
        this.currency = currency;
        this.deadlineDays = deadlineDays;
        this.taskDocument = taskDocument;
        this.parentExerciseId = parentExerciseId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getLanguage() {
        return language;
    }

    public String getStatus() {
        return status;
    }

    public int getVersion() {
        return version;
    }

    public String getComplexity() {
        return complexity;
    }

    public long getPriceCents() {
        return priceCents;
    }

    public String getCurrency() {
        return currency;
    }

    public int getDeadlineDays() {
        return deadlineDays;
    }

    public String getTaskDocument() {
        return taskDocument;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public UUID getParentExerciseId() {
        return parentExerciseId;
    }

    public boolean isSubtask() {
        return parentExerciseId != null;
    }

    public boolean isDraft() {
        return "DRAFT".equals(status);
    }

    public boolean isPublished() {
        return "PUBLISHED".equals(status);
    }

    public void updateDraft(String title, String summary, String language, String complexity,
            long priceCents, String currency, int deadlineDays, String taskDocument) {
        this.title = title;
        this.summary = summary;
        this.language = language;
        this.complexity = complexity;
        this.priceCents = priceCents;
        this.currency = currency;
        this.deadlineDays = deadlineDays;
        this.taskDocument = taskDocument;
        this.updatedAt = Instant.now();
    }

    public void publish(String taskDocument) {
        this.taskDocument = taskDocument;
        this.status = "PUBLISHED";
        this.publishedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void close() {
        this.status = "CLOSED";
        this.updatedAt = Instant.now();
    }
}
