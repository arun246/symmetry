package io.symmetry.contract;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A signed record that a specific worker accepted a specific task document before
 * working. A submission without a matching acceptance is ineligible.
 */
@Entity
@Table(name = "acceptance")
public class Acceptance {

    @Id
    private UUID id;

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @Column(name = "worker_id", nullable = false, columnDefinition = "text")
    private String workerId;

    @Column(name = "task_document_hash", nullable = false, columnDefinition = "text")
    private String taskDocumentHash;

    @Column(name = "accepted_at", nullable = false, columnDefinition = "timestamptz")
    private Instant acceptedAt = Instant.now();

    @Column(nullable = false, columnDefinition = "text")
    private String signature;

    @Column(name = "worker_public_key", nullable = false, columnDefinition = "text")
    private String workerPublicKey;

    @Column(nullable = false)
    private boolean verified;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String agreements;

    /** ACTIVE while the acceptance stands; WITHDRAWN after the worker nullifies it. */
    @Column(nullable = false, columnDefinition = "text")
    private String status = "ACTIVE";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "withdrawal_reasons", columnDefinition = "jsonb")
    private String withdrawalReasons;

    @Column(name = "withdrawal_comment", columnDefinition = "text")
    private String withdrawalComment;

    @Column(name = "withdrawn_at", columnDefinition = "timestamptz")
    private Instant withdrawnAt;

    protected Acceptance() {
    }

    public Acceptance(UUID id, UUID contractId, String workerId, String taskDocumentHash,
            String signature, String workerPublicKey, boolean verified, String agreements) {
        this.id = id;
        this.contractId = contractId;
        this.workerId = workerId;
        this.taskDocumentHash = taskDocumentHash;
        this.signature = signature;
        this.workerPublicKey = workerPublicKey;
        this.verified = verified;
        this.agreements = agreements;
    }

    public UUID getId() {
        return id;
    }

    public UUID getContractId() {
        return contractId;
    }

    public String getWorkerId() {
        return workerId;
    }

    public String getTaskDocumentHash() {
        return taskDocumentHash;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public String getSignature() {
        return signature;
    }

    public String getWorkerPublicKey() {
        return workerPublicKey;
    }

    public boolean isVerified() {
        return verified;
    }

    public String getAgreements() {
        return agreements;
    }

    public String getStatus() {
        return status;
    }

    public boolean isWithdrawn() {
        return "WITHDRAWN".equals(status);
    }

    public String getWithdrawalReasons() {
        return withdrawalReasons;
    }

    public String getWithdrawalComment() {
        return withdrawalComment;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }

    /**
     * Nullifies the acceptance. The caller validates and serializes the reasons;
     * the signed proof (hash, signature, agreements) is left untouched so the
     * record stays auditable.
     */
    public void withdraw(String reasonsJson, String comment) {
        if (isWithdrawn()) {
            throw new IllegalStateException("Acceptance is already withdrawn");
        }
        this.status = "WITHDRAWN";
        this.withdrawalReasons = reasonsJson;
        this.withdrawalComment = comment;
        this.withdrawnAt = Instant.now();
    }
}
