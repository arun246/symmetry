package io.symmetry.admin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.symmetry.analytics.AnalyticsService;
import io.symmetry.api.OrganizationView;
import io.symmetry.api.UserView;
import io.symmetry.audit.AuditLog;
import io.symmetry.audit.AuditLogRepository;
import io.symmetry.contract.PageResponse;
import io.symmetry.events.EventService;
import io.symmetry.identity.Actor;
import io.symmetry.identity.AppUser;
import io.symmetry.identity.AppUserRepository;
import io.symmetry.identity.CurrentUserService;
import io.symmetry.identity.Organization;
import io.symmetry.identity.OrganizationRepository;
import io.symmetry.identity.Role;
import io.symmetry.settings.ConfigKeys;
import io.symmetry.settings.ConfigRequestService;
import io.symmetry.settings.ConfigRequestStatus;
import io.symmetry.settings.ConfigRequestView;
import io.symmetry.settings.ConfigScope;
import io.symmetry.settings.SettingService;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final int MAX_PAGE_SIZE = 200;

    private final CurrentUserService currentUser;
    private final AnalyticsService analytics;
    private final OrganizationRepository organizations;
    private final AppUserRepository users;
    private final AuditLogRepository auditLogs;
    private final SettingService settings;
    private final ConfigRequestService configRequests;
    private final EventService events;
    private final JdbcTemplate jdbc;

    public AdminController(CurrentUserService currentUser, AnalyticsService analytics,
            OrganizationRepository organizations, AppUserRepository users,
            AuditLogRepository auditLogs, SettingService settings,
            ConfigRequestService configRequests, EventService events, JdbcTemplate jdbc) {
        this.currentUser = currentUser;
        this.analytics = analytics;
        this.organizations = organizations;
        this.users = users;
        this.auditLogs = auditLogs;
        this.settings = settings;
        this.configRequests = configRequests;
        this.events = events;
        this.jdbc = jdbc;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        Map<String, Object> body = new LinkedHashMap<>(analytics.overview(null));
        body.put("services", services());
        return body;
    }

    @GetMapping("/analytics/overview")
    public Map<String, Object> analyticsOverview(@RequestParam(defaultValue = "30") int days) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("overview", analytics.overview(null));
        body.put("contractsByState", analytics.contractsByState(null));
        body.put("acceptancesByDay", analytics.acceptancesByDay(null, days));
        body.put("topModules", analytics.topModules(null, 5));
        body.put("verification", analytics.verification(null));
        body.put("funnel", analytics.funnel(null));
        return body;
    }

    @GetMapping("/analytics/contracts-by-state")
    public List<?> contractsByState() {
        return analytics.contractsByState(null);
    }

    @GetMapping("/analytics/acceptances-by-day")
    public List<?> acceptancesByDay(@RequestParam(defaultValue = "30") int days) {
        return analytics.acceptancesByDay(null, days);
    }

    @GetMapping("/analytics/top-modules")
    public List<?> topModules(@RequestParam(defaultValue = "5") int limit) {
        return analytics.topModules(null, limit);
    }

    @GetMapping("/analytics/verification")
    public List<?> verification() {
        return analytics.verification(null);
    }

    @GetMapping("/analytics/funnel")
    public List<?> funnel() {
        return analytics.funnel(null);
    }

    @GetMapping("/organizations")
    public PageResponse<OrganizationView> organizationList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<Organization> result = organizations.findAll(pageable(page, size, "createdAt"));
        return PageResponse.from(result, org -> toView(org));
    }

    @GetMapping("/users")
    public PageResponse<UserView> userList(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UUID orgId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = pageable(page, size, "createdAt");
        Page<AppUser> result;
        if (orgId != null) {
            result = users.findByOrgId(orgId, pageable);
        } else if (role != null) {
            result = users.findByRole(role, pageable);
        } else {
            result = users.findAll(pageable);
        }
        return PageResponse.from(result, user -> toView(user));
    }

    @GetMapping("/troubleshooting/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("database", databaseStatus());
        body.put("services", services());
        body.put("checkedAt", Instant.now());
        return body;
    }

    @GetMapping("/troubleshooting/queues")
    public Map<String, Object> queues() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("evaluations", 0);
        body.put("submissions", 0);
        body.put("pendingConfigRequests",
                analytics.overview(null).get("pendingConfigRequests"));
        body.put("evaluator", "offline");
        return body;
    }

    @GetMapping("/troubleshooting/errors")
    public Map<String, Object> errors() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of());
        body.put("note", "An in-process error buffer is not enabled in this phase.");
        return body;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("keys", ConfigKeys.all());
        body.put("global", settings.listGlobal().stream().map(settings::describe).toList());
        return body;
    }

    @PutMapping("/config/global/{key}")
    public Map<String, Object> setGlobal(@PathVariable String key, @RequestBody ConfigValue body) {
        Actor actor = currentUser.require();
        return settings.describe(settings.set(ConfigScope.GLOBAL, null, key, body.value(), actor));
    }

    @GetMapping("/config/requests")
    public PageResponse<ConfigRequestView> configRequests(
            @RequestParam(required = false) ConfigRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return configRequests.listAll(status, pageable(page, size, "createdAt"));
    }

    @PostMapping("/config/requests/{id}/approve")
    public ConfigRequestView approve(@PathVariable UUID id, @RequestBody ReviewNote body) {
        return configRequests.approve(currentUser.require(), id, body.note());
    }

    @PostMapping("/config/requests/{id}/reject")
    public ConfigRequestView reject(@PathVariable UUID id, @RequestBody ReviewNote body) {
        return configRequests.reject(currentUser.require(), id, body.note());
    }

    @GetMapping("/audit")
    public PageResponse<AuditLog> audit(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "id"));
        Page<AuditLog> result;
        if (action != null && !action.isBlank()) {
            result = auditLogs.findByAction(action, pageable);
        } else if (actorId != null) {
            result = auditLogs.findByActorId(actorId, pageable);
        } else {
            result = auditLogs.findAll(pageable);
        }
        return PageResponse.from(result, entry -> entry);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return events.subscribe("admin");
    }

    private Map<String, Object> services() {
        Map<String, Object> services = new LinkedHashMap<>();
        services.put("api", "online");
        services.put("postgres", databaseStatus());
        services.put("evaluator", "offline");
        services.put("ai", "offline");
        services.put("wasm", "offline");
        return services;
    }

    private String databaseStatus() {
        try {
            Integer one = jdbc.queryForObject("select 1", Integer.class);
            return one != null && one == 1 ? "online" : "degraded";
        } catch (RuntimeException e) {
            return "offline";
        }
    }

    private OrganizationView toView(Organization org) {
        return new OrganizationView(org.getId(), org.getSlug(), org.getName(), org.getStatus(),
                analytics.countContracts(org.getId()), users.findByOrgId(org.getId()).size(),
                org.getCreatedAt());
    }

    private static UserView toView(AppUser user) {
        return new UserView(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getRole().name(), user.getOrgId(), user.getStatus(), user.getCreatedAt());
    }

    private static Pageable pageable(int page, int size, String sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, sort));
    }

    /** Body for a global configuration write. */
    public record ConfigValue(JsonNode value) {
    }

    /** Body for a review decision. */
    public record ReviewNote(String note) {
    }
}
