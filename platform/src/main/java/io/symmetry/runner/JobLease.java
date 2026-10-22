package io.symmetry.runner;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Deadline-bound permission for one runner to work on one contract. */
@Entity
@Table(name = "job_lease")
public class JobLease {

    @Id
    private UUID id;

    @Column(name = "runner_id", nullable = false)
    private UUID runnerId;

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @Column(nullable = false, columnDefinition = "text")
    private String nonce;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private LeaseStatus status = LeaseStatus.ACTIVE;

    @Column(name = "leased_at", nullable = false, columnDefinition = "timestamptz")
    private Instant leasedAt = Instant.now();

    @Column(name = "expires_at", nullable = false, columnDefinition = "timestamptz")
    private Instant expiresAt;

    @Column(name = "completed_at", columnDefinition = "timestamptz")
    private Instant completedAt;

    protected JobLease() {
    }

    public JobLease(UUID id, UUID runnerId, UUID contractId, String nonce,
            Instant leasedAt, Instant expiresAt) {
        this.id = id;
        this.runnerId = runnerId;
        this.contractId = contractId;
        this.nonce = nonce;
        this.leasedAt = leasedAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
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

    public LeaseStatus getStatus() {
        return status;
    }

    public Instant getLeasedAt() {
        return leasedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public boolean isActive() {
        return status == LeaseStatus.ACTIVE;
    }

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void complete(Instant now) {
        this.status = LeaseStatus.COMPLETED;
        this.completedAt = now;
    }

    public void expire() {
        this.status = LeaseStatus.EXPIRED;
    }

    public void revoke() {
        this.status = LeaseStatus.REVOKED;
    }
}
