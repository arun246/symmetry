package io.symmetry.contract;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.symmetry.task.LifecycleState;
import io.symmetry.task.WorkKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A contract is the machine-readable job card. The free-form {@code payload} holds
 * the full JSON document (signature, invariants, budgets, oracle, scoring).
 */
@Entity
@Table(name = "contract", uniqueConstraints = @UniqueConstraint(columnNames = { "module_id", "version" }))
public class Contract {

    @Id
    private UUID id;

    @Column(name = "module_id", nullable = false, columnDefinition = "text")
    private String moduleId;

    @Column(nullable = false, columnDefinition = "text")
    private String version;

    @Column(name = "task_kind", nullable = false, columnDefinition = "text")
    private String taskKind;

    @Column(nullable = false, columnDefinition = "text")
    private String language;

    @Column(name = "disclosure_tier", nullable = false, columnDefinition = "text")
    private String disclosureTier;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_kind", nullable = false, columnDefinition = "text")
    private WorkKind workKind = WorkKind.COMPUTE;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_state", nullable = false, columnDefinition = "text")
    private LifecycleState lifecycleState = LifecycleState.DRAFT;

    @Column(name = "task_document", nullable = false, columnDefinition = "text")
    private String taskDocument;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt = Instant.now();

    protected Contract() {
    }

    public Contract(UUID id, String moduleId, String version, String taskKind, String language,
            String disclosureTier, WorkKind workKind, String taskDocument, String payload) {
        this.id = id;
        this.moduleId = moduleId;
        this.version = version;
        this.taskKind = taskKind;
        this.language = language;
        this.disclosureTier = disclosureTier;
        this.workKind = workKind;
        this.taskDocument = taskDocument;
        this.payload = payload;
    }

    public UUID getId() {
        return id;
    }

    public String getModuleId() {
        return moduleId;
    }

    public String getVersion() {
        return version;
    }

    public String getTaskKind() {
        return taskKind;
    }

    public String getLanguage() {
        return language;
    }

    public String getDisclosureTier() {
        return disclosureTier;
    }

    public WorkKind getWorkKind() {
        return workKind;
    }

    public LifecycleState getLifecycleState() {
        return lifecycleState;
    }

    public String getTaskDocument() {
        return taskDocument;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Advance the lifecycle once a worker has accepted the task document. */
    public void markAccepted() {
        this.lifecycleState = LifecycleState.ACCEPTED;
    }
}
