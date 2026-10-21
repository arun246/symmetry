package io.symmetry.org;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.symmetry.analytics.AnalyticsService;
import io.symmetry.api.UserView;
import io.symmetry.contract.ContractService;
import io.symmetry.contract.PageResponse;
import io.symmetry.events.EventService;
import io.symmetry.identity.Actor;
import io.symmetry.identity.AppUser;
import io.symmetry.identity.AppUserRepository;
import io.symmetry.identity.CurrentUserService;
import io.symmetry.identity.Organization;
import io.symmetry.identity.OrganizationRepository;
import io.symmetry.settings.ConfigRequestService;
import io.symmetry.settings.ConfigRequestStatus;
import io.symmetry.settings.ConfigRequestView;
import io.symmetry.settings.ConfigScope;
import io.symmetry.settings.SettingService;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/org")
@PreAuthorize("hasAnyRole('PUBLISHER','ADMIN')")
public class OrgController {

    private static final int MAX_PAGE_SIZE = 200;

    private final CurrentUserService currentUser;
    private final OrganizationRepository organizations;
    private final AppUserRepository users;
    private final ContractService contracts;
    private final AnalyticsService analytics;
    private final SettingService settings;
    private final ConfigRequestService configRequests;
    private final EventService events;

    public OrgController(CurrentUserService currentUser, OrganizationRepository organizations,
            AppUserRepository users, ContractService contracts, AnalyticsService analytics,
            SettingService settings, ConfigRequestService configRequests, EventService events) {
        this.currentUser = currentUser;
        this.organizations = organizations;
        this.users = users;
        this.contracts = contracts;
        this.analytics = analytics;
        this.settings = settings;
        this.configRequests = configRequests;
        this.events = events;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        UUID orgId = requireOrg();
        Organization org = organizations.findById(orgId).orElseThrow(() -> notFound(orgId));
        Map<String, Object> body = new LinkedHashMap<>(analytics.overview(orgId));
        Map<String, Object> orgBlock = new LinkedHashMap<>();
        orgBlock.put("id", org.getId());
        orgBlock.put("slug", org.getSlug());
        orgBlock.put("name", org.getName());
        body.put("org", orgBlock);
        return body;
    }

    @GetMapping("/contracts")
    public PageResponse<?> contracts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return contracts.listForOrg(requireOrg(), pageable(page, size));
    }

    @GetMapping("/workers")
    public List<UserView> workers() {
        UUID orgId = requireOrg();
        List<UserView> views = new ArrayList<>();
        for (AppUser user : users.findByOrgId(orgId)) {
            views.add(toView(user));
        }
        return views;
    }

    @GetMapping("/evaluations")
    public Map<String, Object> evaluations() {
        UUID orgId = requireOrg();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("overview", analytics.overview(orgId));
        body.put("verification", analytics.verification(orgId));
        body.put("funnel", analytics.funnel(orgId));
        body.put("evaluator", "offline");
        return body;
    }

    @GetMapping("/payments")
    public Map<String, Object> payments() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("currency", "USD");
        body.put("pendingCents", 0);
        body.put("paidCents", 0);
        body.put("items", List.of());
        body.put("note", "Payments are not connected in this phase.");
        return body;
    }

    @GetMapping("/analytics/overview")
    public Map<String, Object> analyticsOverview(
            @RequestParam(defaultValue = "30") int days) {
        UUID orgId = requireOrg();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("overview", analytics.overview(orgId));
        body.put("contractsByState", analytics.contractsByState(orgId));
        body.put("acceptancesByDay", analytics.acceptancesByDay(orgId, days));
        body.put("topModules", analytics.topModules(orgId, 5));
        body.put("verification", analytics.verification(orgId));
        body.put("funnel", analytics.funnel(orgId));
        return body;
    }

    @GetMapping("/analytics/contracts-by-state")
    public List<?> contractsByState() {
        return analytics.contractsByState(requireOrg());
    }

    @GetMapping("/analytics/acceptances-by-day")
    public List<?> acceptancesByDay(@RequestParam(defaultValue = "30") int days) {
        return analytics.acceptancesByDay(requireOrg(), days);
    }

    @GetMapping("/analytics/top-modules")
    public List<?> topModules(@RequestParam(defaultValue = "5") int limit) {
        return analytics.topModules(requireOrg(), limit);
    }

    @GetMapping("/analytics/verification")
    public List<?> verification() {
        return analytics.verification(requireOrg());
    }

    @GetMapping("/analytics/funnel")
    public List<?> funnel() {
        return analytics.funnel(requireOrg());
    }

    @GetMapping("/settings/effective")
    public Map<String, JsonNode> effectiveSettings() {
        return settings.effective(null, null, requireOrg());
    }

    @GetMapping("/config-requests")
    public PageResponse<ConfigRequestView> configRequests(
            @RequestParam(required = false) ConfigRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return configRequests.listForOrg(requireOrg(), status, pageable(page, size));
    }

    @PostMapping("/config-requests")
    public ConfigRequestView requestConfig(@RequestBody ConfigRequestInput input) {
        return configRequests.create(currentUser.require(), input.contractId(), input.key(),
                input.value(), input.reason());
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return events.subscribe("org:" + requireOrg());
    }

    private UUID requireOrg() {
        Actor actor = currentUser.require();
        if (actor.orgId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This account is not attached to an organization");
        }
        return actor.orgId();
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private static UserView toView(AppUser user) {
        return new UserView(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getRole().name(), user.getOrgId(), user.getStatus(), user.getCreatedAt());
    }

    private static ResponseStatusException notFound(UUID orgId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization " + orgId + " not found");
    }

    /** Request body for a configuration tweak scoped to the caller's organization. */
    public record ConfigRequestInput(UUID contractId, String key, JsonNode value, String reason) {
    }
}
