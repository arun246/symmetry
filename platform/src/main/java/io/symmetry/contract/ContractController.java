package io.symmetry.contract;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/contracts")
public class ContractController {

    private static final int MAX_PAGE_SIZE = 200;

    private final ContractService service;

    public ContractController(ContractService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContractView create(@Valid @RequestBody ContractRequest request) {
        return service.create(request);
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
    public Acceptance accept(@PathVariable UUID id, @Valid @RequestBody AcceptRequest request) {
        return service.accept(id, request);
    }
}
