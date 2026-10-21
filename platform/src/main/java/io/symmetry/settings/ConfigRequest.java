package io.symmetry.settings;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An organization's request for a scoped configuration tweak, reviewed by an admin. */
@Entity
@Table(name = "config_request")
public class ConfigRequest {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "contract_id")
    private UUID contractId;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(nullable = false, columnDefinition = "text")
    private String key;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String value;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private ConfigRequestStatus status = ConfigRequestStatus.PENDING;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at", columnDefinition = "timestamptz")
    private Instant reviewedAt;

    @Column(name = "review_note", columnDefinition = "text")
    private String reviewNote;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt = Instant.now();

    protected ConfigRequest() {
    }

    public ConfigRequest(UUID id, UUID orgId, UUID contractId, UUID requestedBy,
            String key, String value, String reason) {
        this.id = id;
        this.orgId = orgId;
        this.contractId = contractId;
        this.requestedBy = requestedBy;
        this.key = key;
        this.value = value;
        this.reason = reason;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getContractId() {
        return contractId;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public String getReason() {
        return reason;
    }

    public ConfigRequestStatus getStatus() {
        return status;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void approve(UUID reviewer, String note) {
        this.status = ConfigRequestStatus.APPLIED;
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
        this.reviewNote = note;
    }

    public void reject(UUID reviewer, String note) {
        this.status = ConfigRequestStatus.REJECTED;
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
        this.reviewNote = note;
    }
}
