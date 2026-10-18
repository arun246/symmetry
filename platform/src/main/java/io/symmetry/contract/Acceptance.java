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
}
