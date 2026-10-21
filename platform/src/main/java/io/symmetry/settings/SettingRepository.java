package io.symmetry.settings;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingRepository extends JpaRepository<Setting, UUID> {

    /** GLOBAL rows have a null scope id, so they use the key-only lookup. */
    Optional<Setting> findByScopeAndKey(ConfigScope scope, String key);

    Optional<Setting> findByScopeAndScopeIdAndKey(ConfigScope scope, UUID scopeId, String key);

    List<Setting> findByScope(ConfigScope scope);

    List<Setting> findByScopeAndScopeId(ConfigScope scope, UUID scopeId);
}
