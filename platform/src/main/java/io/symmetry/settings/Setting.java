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

/** One scoped configuration value. Resolution order lives in {@link SettingService}. */
@Entity
@Table(name = "setting")
public class Setting {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private ConfigScope scope;

    @Column(name = "scope_id")
    private UUID scopeId;

    @Column(nullable = false, columnDefinition = "text")
    private String key;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String value;

    @Column(nullable = false)
    private int version = 1;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt = Instant.now();

    protected Setting() {
    }

    public Setting(UUID id, ConfigScope scope, UUID scopeId, String key, String value, UUID updatedBy) {
        this.id = id;
        this.scope = scope;
        this.scopeId = scopeId;
        this.key = key;
        this.value = value;
        this.updatedBy = updatedBy;
    }

    public UUID getId() {
        return id;
    }

    public ConfigScope getScope() {
        return scope;
    }

    public UUID getScopeId() {
        return scopeId;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public int getVersion() {
        return version;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(String value, UUID updatedBy) {
        this.value = value;
        this.updatedBy = updatedBy;
        this.version += 1;
        this.updatedAt = Instant.now();
    }
}
