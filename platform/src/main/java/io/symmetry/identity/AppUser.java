package io.symmetry.identity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Local mirror of an identity. Keycloak owns credentials and MFA; this row holds
 * the platform role and organization so the API can authorize and scope queries
 * without a round-trip to the identity server on every request.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    private UUID id;

    @Column(nullable = false, columnDefinition = "text")
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String email;

    @Column(name = "display_name", nullable = false, columnDefinition = "text")
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private Role role;

    @Column(name = "org_id")
    private UUID orgId;

    @Column(nullable = false, columnDefinition = "text")
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt = Instant.now();

    protected AppUser() {
    }

    public AppUser(UUID id, String subject, String email, String displayName, Role role, UUID orgId) {
        this.id = id;
        this.subject = subject;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
        this.orgId = orgId;
    }

    public UUID getId() {
        return id;
    }

    public String getSubject() {
        return subject;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Role getRole() {
        return role;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public void setOrgId(UUID orgId) {
        this.orgId = orgId;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
