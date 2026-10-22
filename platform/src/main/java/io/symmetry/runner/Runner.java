package io.symmetry.runner;

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

/**
 * A registered publisher appliance. The raw access token is never stored; only
 * its SHA-256 hash is kept so a database leak does not hand out runner access.
 */
@Entity
@Table(name = "runner")
public class Runner {

    @Id
    private UUID id;

    @Column(nullable = false, columnDefinition = "text")
    private String name;

    @Column(nullable = false, columnDefinition = "text")
    private String hostname;

    @Column(name = "org_id")
    private UUID orgId;

    @Column(name = "public_key", nullable = false, columnDefinition = "text")
    private String publicKey;

    @Column(name = "access_token_hash", nullable = false, columnDefinition = "text")
    private String accessTokenHash;

    @Column(nullable = false, columnDefinition = "text")
    private String version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String capabilities;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private RunnerStatus status = RunnerStatus.ACTIVE;

    @Column(name = "last_seen_at", columnDefinition = "timestamptz")
    private Instant lastSeenAt;

    @Column(name = "registered_at", nullable = false, columnDefinition = "timestamptz")
    private Instant registeredAt = Instant.now();

    @Column(name = "revoked_at", columnDefinition = "timestamptz")
    private Instant revokedAt;

    protected Runner() {
    }

    public Runner(UUID id, String name, String hostname, UUID orgId, String publicKey,
            String accessTokenHash, String version, String capabilities) {
        this.id = id;
        this.name = name;
        this.hostname = hostname;
        this.orgId = orgId;
        this.publicKey = publicKey;
        this.accessTokenHash = accessTokenHash;
        this.version = version;
        this.capabilities = capabilities;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getHostname() {
        return hostname;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getAccessTokenHash() {
        return accessTokenHash;
    }

    public String getVersion() {
        return version;
    }

    public String getCapabilities() {
        return capabilities;
    }

    public RunnerStatus getStatus() {
        return status;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public boolean isActive() {
        return status == RunnerStatus.ACTIVE;
    }

    public void heartbeat(Instant now, String version, String capabilities) {
        this.lastSeenAt = now;
        this.version = version;
        this.capabilities = capabilities;
    }

    public void revoke(Instant now) {
        this.status = RunnerStatus.REVOKED;
        this.revokedAt = now;
    }
}
