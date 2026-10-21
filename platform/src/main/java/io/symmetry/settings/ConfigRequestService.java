package io.symmetry.settings;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import io.symmetry.audit.AuditService;
import io.symmetry.contract.PageResponse;
import io.symmetry.identity.Actor;
import tools.jackson.databind.JsonNode;

/**
 * Organizations ask; admins review. Approval applies the value at ORG or CONTRACT
 * scope and records both the request decision and the setting change in the audit
 * log, so a tweak can always be traced back to who asked and who allowed it.
 */
@Service
public class ConfigRequestService {

    private final ConfigRequestRepository requests;
    private final SettingService settings;
    private final AuditService audit;

    public ConfigRequestService(ConfigRequestRepository requests, SettingService settings,
            AuditService audit) {
        this.requests = requests;
        this.settings = settings;
        this.audit = audit;
    }

    @Transactional
    public ConfigRequestView create(Actor actor, UUID contractId, String key, JsonNode value,
            String reason) {
        if (actor.orgId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only organization members can request configuration changes");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required for a configuration request");
        }
        ConfigKeys.validate(key, value);
        if (!ConfigKeys.allows(contractId == null ? ConfigScope.ORG : ConfigScope.CONTRACT, key)) {
            throw new IllegalArgumentException("'" + key + "' is not an organization-scoped setting");
        }
        ConfigRequest request = requests.save(new ConfigRequest(UUID.randomUUID(), actor.orgId(),
                contractId, actor.id(), key, settings.write(value), reason));
        audit.record(actor, "config.request", "config_request", request.getId().toString(), null,
                Map.of("key", key, "scope", contractId == null ? "ORG" : "CONTRACT",
                        "orgId", actor.orgId().toString()));
        return ConfigRequestView.from(request, value);
    }

    public PageResponse<ConfigRequestView> listForOrg(UUID orgId, ConfigRequestStatus status,
            Pageable pageable) {
        Page<ConfigRequest> page = status == null
                ? requests.findByOrgId(orgId, pageable)
                : requests.findByOrgIdAndStatus(orgId, status, pageable);
        return PageResponse.from(page, this::toView);
    }

    public PageResponse<ConfigRequestView> listAll(ConfigRequestStatus status, Pageable pageable) {
        Page<ConfigRequest> page = status == null
                ? requests.findAll(pageable)
                : requests.findByStatus(status, pageable);
        return PageResponse.from(page, this::toView);
    }

    @Transactional
    public ConfigRequestView approve(Actor admin, UUID id, String note) {
        ConfigRequest request = pending(id);
        ConfigScope scope = request.getContractId() == null ? ConfigScope.ORG : ConfigScope.CONTRACT;
        UUID scopeId = request.getContractId() == null ? request.getOrgId() : request.getContractId();
        JsonNode value = settings.read(request.getValue());
        settings.set(scope, scopeId, request.getKey(), value, admin);
        request.approve(admin.id(), note);
        ConfigRequest saved = requests.save(request);
        audit.record(admin, "config.approve", "config_request", id.toString(), null,
                Map.of("key", request.getKey(), "scope", scope.name(),
                        "scopeId", scopeId.toString()));
        return ConfigRequestView.from(saved, value);
    }

    @Transactional
    public ConfigRequestView reject(Actor admin, UUID id, String note) {
        ConfigRequest request = pending(id);
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("A review note is required when rejecting");
        }
        request.reject(admin.id(), note);
        ConfigRequest saved = requests.save(request);
        audit.record(admin, "config.reject", "config_request", id.toString(), null,
                Map.of("key", request.getKey(), "note", note));
        return ConfigRequestView.from(saved, settings.read(request.getValue()));
    }

    private ConfigRequest pending(UUID id) {
        ConfigRequest request = requests.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Configuration request " + id + " not found"));
        if (request.getStatus() != ConfigRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Request is already " + request.getStatus().name().toLowerCase());
        }
        return request;
    }

    private ConfigRequestView toView(ConfigRequest request) {
        return ConfigRequestView.from(request, settings.read(request.getValue()));
    }
}
