package io.symmetry.exercise;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.symmetry.audit.AuditService;
import io.symmetry.contract.PageResponse;
import io.symmetry.events.EventService;
import io.symmetry.identity.Actor;
import io.symmetry.identity.CurrentUserService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/exercises")
public class ExerciseController {

    private static final String CHANNEL = "exercises";

    private final ExerciseService service;
    private final GitHubImportService github;
    private final CurrentUserService currentUser;
    private final AuditService audit;
    private final EventService events;

    public ExerciseController(ExerciseService service, GitHubImportService github,
            CurrentUserService currentUser, AuditService audit, EventService events) {
        this.service = service;
        this.github = github;
        this.currentUser = currentUser;
        this.audit = audit;
        this.events = events;
    }

    @GetMapping
    public PageResponse<ExerciseView> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "publishedAt"));
        return service.listPublic(pageable);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('PUBLISHER','ADMIN')")
    public PageResponse<ExerciseView> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Actor actor = currentUser.require();
        Pageable pageable = PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return service.listMine(actor, pageable);
    }

    @GetMapping("/accepted")
    @PreAuthorize("hasAnyRole('WORKER','ADMIN')")
    public List<ExerciseAcceptanceView> accepted() {
        return service.acceptedForWorker(currentUser.require());
    }

    @GetMapping("/{id}")
    public ExerciseDetailView detail(@PathVariable UUID id) {
        Actor actor = currentUser.maybe().orElse(null);
        return service.detail(id, actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PUBLISHER','ADMIN','WORKER')")
    public ExerciseDetailView create(@Valid @RequestBody CreateExerciseRequest request) {
        Actor actor = currentUser.require();
        ExerciseDetailView view = service.create(actor, request);
        audit.record(actor, "exercise.create", "exercise", view.id().toString(), null,
                Map.of("title", view.title(), "language", view.language()));
        events.publish(CHANNEL, "exercise.created", Map.of("id", view.id().toString()));
        return view;
    }

    @PostMapping("/{id}/subtasks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WORKER')")
    public ExerciseDetailView createSubtask(@PathVariable UUID id,
            @Valid @RequestBody CreateSubtaskRequest request) {
        Actor actor = currentUser.require();
        ExerciseDetailView view = service.createSubtask(id, actor, request);
        audit.record(actor, "exercise.subtask.create", "exercise", view.id().toString(), null,
                Map.of("parentExerciseId", id.toString(), "title", view.title(),
                        "complexity", view.complexity()));
        events.publish(CHANNEL, "exercise.subtask.created", Map.of(
                "id", view.id().toString(), "parentExerciseId", id.toString()));
        return view;
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('PUBLISHER','ADMIN','WORKER')")
    public ExerciseDetailView update(@PathVariable UUID id,
            @Valid @RequestBody UpdateExerciseRequest request) {
        Actor actor = currentUser.require();
        ExerciseDetailView view = service.update(id, actor, request);
        audit.record(actor, "exercise.update", "exercise", id.toString(), null,
                Map.of("title", view.title()));
        return view;
    }

    @GetMapping("/github/tree")
    @PreAuthorize("hasAnyRole('PUBLISHER','ADMIN','WORKER')")
    public RepoTreeView tree(@RequestParam String repoUrl) {
        return github.preview(repoUrl);
    }

    @PostMapping("/{id}/source")
    @PreAuthorize("hasAnyRole('PUBLISHER','ADMIN','WORKER')")
    public SourceView importSource(@PathVariable UUID id,
            @Valid @RequestBody ImportSourceRequest request) {
        Actor actor = currentUser.require();
        SourceView view = service.importSource(id, actor, request);
        audit.record(actor, "exercise.source.import", "exercise", id.toString(), null,
                Map.of("repoUrl", view.repoUrl(), "commitSha", view.commitSha(),
                        "filePath", view.filePath(), "findings", view.findings().size()));
        return view;
    }

    @GetMapping("/{id}/symbols")
    @PreAuthorize("hasAnyRole('PUBLISHER','ADMIN','WORKER')")
    public List<SymbolView> symbols(@PathVariable UUID id) {
        return service.symbols(id, currentUser.require());
    }

    @PostMapping("/{id}/stubs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PUBLISHER','ADMIN','WORKER')")
    public StubView addStub(@PathVariable UUID id,
            @Valid @RequestBody ExtractStubRequest request) {
        Actor actor = currentUser.require();
        StubView view = service.addStub(id, actor, request);
        audit.record(actor, "exercise.stub.create", "exercise", id.toString(), null,
                Map.of("symbol", view.symbolName(), "startLine", view.startLine()));
        return view;
    }

    @PutMapping("/{id}/tests")
    @PreAuthorize("hasAnyRole('PUBLISHER','ADMIN','WORKER')")
    public List<ExerciseTestView> saveTests(@PathVariable UUID id,
            @Valid @RequestBody SaveTestsRequest request) {
        Actor actor = currentUser.require();
        List<ExerciseTestView> views = service.saveTests(id, actor, request);
        audit.record(actor, "exercise.tests.save", "exercise", id.toString(), null,
                Map.of("tests", views.size()));
        return views;
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('PUBLISHER','ADMIN','WORKER')")
    public ExerciseDetailView publish(@PathVariable UUID id) {
        Actor actor = currentUser.require();
        ExerciseDetailView view = service.publish(id, actor);
        audit.record(actor, "exercise.publish", "exercise", id.toString(), null,
                Map.of("title", view.title(), "priceCents", view.priceCents()));
        events.publish(CHANNEL, "exercise.published", Map.of("id", id.toString()));
        return view;
    }

    @PostMapping("/{id}/accept")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('WORKER','ADMIN')")
    public ExerciseAcceptanceView accept(@PathVariable UUID id,
            @Valid @RequestBody AcceptExerciseRequest request) {
        Actor actor = currentUser.require();
        ExerciseAcceptance acceptance = service.accept(id, actor, request);
        audit.record(actor, "exercise.accept", "exercise", id.toString(), null,
                Map.of("acceptanceId", acceptance.getId().toString(),
                        "deadlineAt", acceptance.getDeadlineAt().toString()));
        events.publish(CHANNEL, "exercise.accepted", Map.of("id", id.toString(),
                "workerId", acceptance.getWorkerId()));
        return service.acceptanceView(acceptance);
    }

    @GetMapping("/{id}/workspace")
    @PreAuthorize("hasAnyRole('WORKER','ADMIN')")
    public WorkspaceView workspace(@PathVariable UUID id) {
        return service.workspace(id, currentUser.require());
    }

    @PostMapping("/{id}/static-check")
    @PreAuthorize("hasAnyRole('WORKER','ADMIN')")
    public StaticCheckResult staticCheck(@PathVariable UUID id,
            @Valid @RequestBody StaticCheckRequest request) {
        return service.staticCheck(id, currentUser.require(), request);
    }

    @PostMapping("/{id}/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('WORKER','ADMIN')")
    public SubmissionView submit(@PathVariable UUID id,
            @Valid @RequestBody SubmissionRequest request) {
        Actor actor = currentUser.require();
        SubmissionView view = service.submit(id, actor, request);
        audit.record(actor, "exercise.submit", "exercise", id.toString(), null,
                Map.of("submissionId", view.id().toString(), "status", view.status()));
        events.publish(CHANNEL, "exercise.submitted", Map.of("id", id.toString(),
                "submissionId", view.id().toString(), "status", view.status()));
        return view;
    }

    @GetMapping("/{id}/submissions")
    @PreAuthorize("hasAnyRole('PUBLISHER','ADMIN','WORKER')")
    public List<SubmissionView> submissions(@PathVariable UUID id) {
        return service.submissionsForExercise(id, currentUser.require());
    }
}
