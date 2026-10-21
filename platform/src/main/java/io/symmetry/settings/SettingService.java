package io.symmetry.settings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import io.symmetry.audit.AuditService;
import io.symmetry.identity.Actor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Reads and writes scoped settings with USER &gt; CONTRACT &gt; ORG &gt; GLOBAL precedence. */
@Service
public class SettingService {

    private final SettingRepository settings;
    private final AuditService audit;
    private final ObjectMapper mapper;

    public SettingService(SettingRepository settings, AuditService audit, ObjectMapper mapper) {
        this.settings = settings;
        this.audit = audit;
        this.mapper = mapper;
    }

    public Optional<JsonNode> resolve(String key, UUID userId, UUID contractId, UUID orgId) {
        return find(ConfigScope.USER, userId, key)
                .or(() -> find(ConfigScope.CONTRACT, contractId, key))
                .or(() -> find(ConfigScope.ORG, orgId, key))
                .or(() -> find(ConfigScope.GLOBAL, null, key));
    }

    public Map<String, JsonNode> effective(UUID userId, UUID contractId, UUID orgId) {
        Map<String, JsonNode> values = new TreeMap<>();
        for (String key : ConfigKeys.all()) {
            resolve(key, userId, contractId, orgId).ifPresent(value -> values.put(key, value));
        }
        return values;
    }

    public List<Setting> listGlobal() {
        return settings.findByScope(ConfigScope.GLOBAL);
    }

    @Transactional
    public Setting set(ConfigScope scope, UUID scopeId, String key, JsonNode value, Actor actor) {
        ConfigKeys.validate(key, value);
        if (!ConfigKeys.allows(scope, key)) {
            throw new IllegalArgumentException(
                    "'" + key + "' cannot be set at " + scope + " scope");
        }
        if (scope == ConfigScope.GLOBAL && scopeId != null) {
            throw new IllegalArgumentException("GLOBAL settings must not have a scope id");
        }
        if (scope != ConfigScope.GLOBAL && scopeId == null) {
            throw new IllegalArgumentException(scope + " settings need a scope id");
        }

        Optional<Setting> existing = scope == ConfigScope.GLOBAL
                ? settings.findByScopeAndKey(ConfigScope.GLOBAL, key)
                : settings.findByScopeAndScopeIdAndKey(scope, scopeId, key);

        String json = write(value);
        Setting setting = existing.orElseGet(
                () -> new Setting(UUID.randomUUID(), scope, scopeId, key, json,
                        actor == null ? null : actor.id()));
        String before = existing.map(Setting::getValue).orElse(null);
        if (existing.isPresent()) {
            setting.update(json, actor == null ? null : actor.id());
        }
        Setting saved = settings.save(setting);
        audit.record(actor, "config.update", "setting", saved.getId().toString(),
                before == null ? null : read(before),
                Map.of("key", key, "scope", scope.name(), "value", value));
        return saved;
    }

    private Optional<JsonNode> find(ConfigScope scope, UUID scopeId, String key) {
        Optional<Setting> setting = scope == ConfigScope.GLOBAL
                ? settings.findByScopeAndKey(ConfigScope.GLOBAL, key)
                : (scopeId == null ? Optional.empty()
                        : settings.findByScopeAndScopeIdAndKey(scope, scopeId, key));
        return setting.map(value -> read(value.getValue()));
    }

    public JsonNode read(String json) {
        try {
            return mapper.readTree(json);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Stored setting is not valid JSON", e);
        }
    }

    public String write(JsonNode value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Setting value is not valid JSON", e);
        }
    }

    public Map<String, Object> describe(Setting setting) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", setting.getId());
        row.put("scope", setting.getScope());
        row.put("scopeId", setting.getScopeId());
        row.put("key", setting.getKey());
        row.put("value", read(setting.getValue()));
        row.put("version", setting.getVersion());
        row.put("updatedBy", setting.getUpdatedBy());
        row.put("updatedAt", setting.getUpdatedAt());
        return row;
    }

    public Setting requireScope(ConfigScope scope, UUID scopeId, String key) {
        return settings.findByScopeAndScopeIdAndKey(scope, scopeId, key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No " + scope + " setting for " + key));
    }
}
