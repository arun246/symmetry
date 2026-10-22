package io.symmetry.runner;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A runner-signed, sanitized verdict bound to one lease. */
@Entity
@Table(name = "runner_result")
public class RunnerResult {

    @Id
    private UUID id;

    @Column(name = "lease_id", nullable = false)
    private UUID leaseId;

    @Column(name = "runner_id", nullable = false)
    private UUID runnerId;

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @Column(nullable = false, columnDefinition = "text")
    private String nonce;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "payload_hash", nullable = false, columnDefinition = "text")
    private String payloadHash;

    @Column(nullable = false, columnDefinition = "text")
    private String signature;

    @Column(nullable = false)
    private boolean passed;

    @Column(name = "artifact_digest", columnDefinition = "text")
    private String artifactDigest;

    @Column(name = "logs_digest", columnDefinition = "text")
    private String logsDigest;

    @Column(nullable = false)
    private boolean quarantined;

    @Column(name = "received_at", nullable = false, columnDefinition = "timestamptz")
    private Instant receivedAt = Instant.now();

    protected RunnerResult() {
    }

    public RunnerResult(UUID id, UUID leaseId, UUID runnerId, UUID contractId, String nonce,
            String payload, String payloadHash, String signature, boolean passed,
            String artifactDigest, String logsDigest) {
        this.id = id;
        this.leaseId = leaseId;
        this.runnerId = runnerId;
        this.contractId = contractId;
        this.nonce = nonce;
        this.payload = payload;
        this.payloadHash = payloadHash;
        this.signature = signature;
        this.passed = passed;
        this.artifactDigest = artifactDigest;
        this.logsDigest = logsDigest;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLeaseId() {
        return leaseId;
    }

    public UUID getRunnerId() {
        return runnerId;
    }

    public UUID getContractId() {
        return contractId;
    }

    public String getNonce() {
        return nonce;
    }

    public String getPayload() {
        return payload;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public String getSignature() {
        return signature;
    }

    public boolean isPassed() {
        return passed;
    }

    public String getArtifactDigest() {
        return artifactDigest;
    }

    public String getLogsDigest() {
        return logsDigest;
    }

    public boolean isQuarantined() {
        return quarantined;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
