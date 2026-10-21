package io.symmetry.contract;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.symmetry.audit.AuditService;
import io.symmetry.events.EventService;
import io.symmetry.identity.Actor;
import io.symmetry.identity.CurrentUserService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/contracts")
public class ContractController {

    private static final int MAX_PAGE_SIZE = 200;
    private static final String CHANNEL = "contracts";

    private final ContractService service;
    private final CurrentUserService currentUser;
    private final AuditService audit;
    private final EventService events;

    public ContractController(ContractService service, CurrentUserService currentUser,
            AuditService audit, EventService events) {
        this.service = service;
        this.currentUser = currentUser;
        this.audit = audit;
        this.events = events;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PUBLISHER','ADMIN')")
    public ContractView create(@Valid @RequestBody ContractRequest request) {
        Actor actor = currentUser.require();
        ContractView view = service.create(request, actor);
        audit.record(actor, "contract.publish", "contract", view.id().toString(), null,
                Map.of("moduleId", view.moduleId(), "version", view.version(),
                        "taskKind", view.taskKind()));
        events.publish(CHANNEL, "contract.published", Map.of(
                "id", view.id().toString(),
                "moduleId", view.moduleId(),
                "version", view.version()));
        return view;
    }

    @GetMapping
    public PageResponse<ContractView> list(
            @RequestParam(required = false) String moduleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return service.list(moduleId, pageable);
    }

    @GetMapping("/{moduleId}/{version}")
    public ContractView get(@PathVariable String moduleId, @PathVariable String version) {
        return service.get(moduleId, version);
    }

    @GetMapping("/{moduleId}/{version}/workbench")
    public WorkbenchView workbench(@PathVariable String moduleId, @PathVariable String version) {
        return service.workbench(moduleId, version);
    }

    @PostMapping("/{id}/accept")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WORKER')")
    public Acceptance accept(@PathVariable UUID id, @Valid @RequestBody AcceptRequest request) {
        Actor actor = currentUser.require();
        Acceptance acceptance = service.accept(id, request);
        audit.record(actor, "contract.accept", "contract", id.toString(), null,
                Map.of("acceptanceId", acceptance.getId().toString(),
                        "workerId", acceptance.getWorkerId()));
        events.publish(CHANNEL, "contract.accepted", Map.of(
                "contractId", id.toString(),
                "acceptanceId", acceptance.getId().toString(),
                "workerId", acceptance.getWorkerId()));
        return acceptance;
    }
}
