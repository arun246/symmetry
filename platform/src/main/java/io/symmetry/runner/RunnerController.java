package io.symmetry.runner;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.symmetry.identity.Actor;
import io.symmetry.identity.CurrentUserService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/runners")
public class RunnerController {

    private final RunnerService service;
    private final CurrentUserService currentUser;

    public RunnerController(RunnerService service, CurrentUserService currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisteredRunnerView register(@Valid @RequestBody RegisterRunnerRequest request) {
        return service.register(request);
    }

    @PostMapping("/{id}/heartbeat")
    public HeartbeatView heartbeat(@PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody(required = false) HeartbeatRequest request) {
        return service.heartbeat(id, authorization, request);
    }

    @PostMapping("/{id}/lease")
    public ResponseEntity<LeaseView> lease(@PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return service.lease(id, authorization)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{id}/results")
    public ResultView submitResult(@PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody SubmitResultRequest request) {
        return service.submitResult(id, authorization, request);
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    public RunnerView revoke(@PathVariable UUID id) {
        Actor actor = currentUser.require();
        return service.revoke(id, actor);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<RunnerView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public RunnerView get(@PathVariable UUID id) {
        return service.get(id);
    }
}
