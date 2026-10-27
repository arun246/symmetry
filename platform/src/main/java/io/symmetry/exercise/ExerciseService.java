package io.symmetry.exercise;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import io.symmetry.contract.MessageSigner;
import io.symmetry.contract.PageResponse;
import io.symmetry.identity.Actor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ExerciseService {

    private static final Set<String> COMPLEXITIES = Set.of("TRIVIAL", "EASY", "MEDIUM", "HARD", "EXPERT");
    private static final Set<String> VISIBILITIES = Set.of("PUBLIC", "HIDDEN");

    private final ExerciseRepository exercises;
    private final ExerciseSourceRepository sources;
    private final ExerciseStubRepository stubs;
    private final ExerciseTestRepository tests;
    private final ExerciseSideCaseRepository sideCases;
    private final ExerciseAcceptanceRepository acceptances;
    private final ExerciseSubmissionRepository submissions;
    private final ExerciseWorkspaceSessionRepository sessions;
    private final GitHubImportService importer;
    private final StaticCheckService staticChecks;
    private final Map<String, StubExtractor> extractors;
    private final ObjectMapper mapper;
    private final Set<String> requiredAgreements;

    public ExerciseService(
            ExerciseRepository exercises,
            ExerciseSourceRepository sources,
            ExerciseStubRepository stubs,
            ExerciseTestRepository tests,
            ExerciseSideCaseRepository sideCases,
            ExerciseAcceptanceRepository acceptances,
            ExerciseSubmissionRepository submissions,
            ExerciseWorkspaceSessionRepository sessions,
            GitHubImportService importer,
            StaticCheckService staticChecks,
            List<StubExtractor> extractors,
            ObjectMapper mapper,
            @Value("${symmetry.exercise.required-agreements:rights,confidentiality,ai-declaration,responsibility,license}")
            String requiredAgreements) {
        this.exercises = exercises;
        this.sources = sources;
        this.stubs = stubs;
        this.tests = tests;
        this.sideCases = sideCases;
        this.acceptances = acceptances;
        this.submissions = submissions;
        this.sessions = sessions;
        this.importer = importer;
        this.staticChecks = staticChecks;
        this.mapper = mapper;
        this.extractors = new LinkedHashMap<>();
        for (StubExtractor extractor : extractors) {
            this.extractors.put(extractor.language().toLowerCase(Locale.ROOT), extractor);
        }
        this.requiredAgreements = Arrays.stream(requiredAgreements.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Transactional
    public ExerciseDetailView create(Actor actor, CreateExerciseRequest request) {
        requirePublisher(actor);
        String language = request.language().toLowerCase(Locale.ROOT);
        requireLanguage(language);
        String complexity = request.complexity().toUpperCase(Locale.ROOT);
        if (!COMPLEXITIES.contains(complexity)) {
            throw badRequest("complexity must be one of " + COMPLEXITIES);
        }
        Exercise exercise = exercises.save(new Exercise(UUID.randomUUID(), actor.orgId(), actor.id(),
                request.title().trim(), request.summary().trim(), language, complexity,
                request.priceCents(), request.currency().toUpperCase(Locale.ROOT),
                request.deadlineDays(), request.taskDocument() == null ? "" : request.taskDocument().trim()));
        return detail(exercise, actor);
    }

    @Transactional
    public ExerciseDetailView createSubtask(UUID parentId, Actor actor, CreateSubtaskRequest request) {
        requireWorker(actor);
        Exercise parent = exercises.findById(parentId).orElseThrow(() -> notFound(parentId));
        if (!parent.isPublished()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Parent task is not published");
        }
        ExerciseAcceptance acceptance = acceptances.findByExerciseIdAndWorkerId(parentId, actor.workerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Accept the parent task before publishing a subtask"));
        if ("EXPIRED".equals(acceptance.getStatus()) || "WITHDRAWN".equals(acceptance.getStatus())
                || acceptance.getDeadlineAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "The parent acceptance has expired; accept it again before delegating work");
        }
        String complexity = request.complexity().toUpperCase(Locale.ROOT);
        if (!COMPLEXITIES.contains(complexity)) {
            throw badRequest("complexity must be one of " + COMPLEXITIES);
        }
        Exercise subtask = exercises.save(new Exercise(UUID.randomUUID(), parent.getOrgId(), actor.id(),
                request.title().trim(), request.summary().trim(), "java", complexity,
                0, parent.getCurrency(), request.deadlineDays(),
                request.taskDocument() == null ? "" : request.taskDocument().trim(), parentId));
        return detail(subtask, actor);
    }

    @Transactional
    public ExerciseDetailView update(UUID exerciseId, Actor actor, UpdateExerciseRequest request) {
        Exercise exercise = requireDraftOwner(exerciseId, actor);
        String language = request.language().toLowerCase(Locale.ROOT);
        requireLanguage(language);
        String complexity = request.complexity().toUpperCase(Locale.ROOT);
        if (!COMPLEXITIES.contains(complexity)) {
            throw badRequest("complexity must be one of " + COMPLEXITIES);
        }
        exercise.updateDraft(request.title().trim(), request.summary().trim(), language, complexity,
                request.priceCents(), request.currency().toUpperCase(Locale.ROOT),
                request.deadlineDays(), request.taskDocument() == null ? "" : request.taskDocument().trim());
        return detail(exercises.save(exercise), actor);
    }

    public PageResponse<ExerciseView> listPublic(Pageable pageable) {
        Page<Exercise> page = exercises.findByStatus("PUBLISHED", pageable);
        return PageResponse.from(page, this::toView);
    }

    public PageResponse<ExerciseView> listMine(Actor actor, Pageable pageable) {
        requirePublisher(actor);
        return PageResponse.from(exercises.findByOrgIdOrderByCreatedAtDesc(actor.orgId(), pageable),
                this::toView);
    }

    public ExerciseDetailView detail(UUID exerciseId, Actor actor) {
        Exercise exercise = exercises.findById(exerciseId)
                .orElseThrow(() -> notFound(exerciseId));
        boolean owner = canManage(exercise, actor);
        if (!exercise.isPublished() && !owner) {
            throw notFound(exerciseId);
        }
        return detail(exercise, actor);
    }

    @Transactional
    public SourceView importSource(UUID exerciseId, Actor actor, ImportSourceRequest request) {
        Exercise exercise = requireDraftOwner(exerciseId, actor);
        ImportedSource imported = importer.importFile(exercise, request);
        return importer.view(imported.source(), true, imported.findings());
    }

    public List<SymbolView> symbols(UUID exerciseId, Actor actor) {
        requireDraftOwner(exerciseId, actor);
        ExerciseSource source = requireSource(exerciseId);
        return extractor(source.getLanguage()).symbols(source.getOriginalContent());
    }

    @Transactional
    public StubView addStub(UUID exerciseId, Actor actor, ExtractStubRequest request) {
        Exercise exercise = requireDraftOwner(exerciseId, actor);
        ExerciseSource source = requireSource(exerciseId);
        StubExtraction extraction = extractor(source.getLanguage()).extract(source.getOriginalContent(), request);
        ExerciseStub stub = new ExerciseStub(UUID.randomUUID(), exercise.getId(),
                extraction.symbolName(), extraction.symbolKind(), extraction.startLine(),
                extraction.endLine(), extraction.stubContent(), extraction.originalHash());
        try {
            return toStubView(stubs.saveAndFlush(stub));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That symbol range is already stubbed");
        }
    }

    @Transactional
    public List<ExerciseTestView> saveTests(UUID exerciseId, Actor actor, SaveTestsRequest request) {
        Exercise exercise = requireDraftOwner(exerciseId, actor);
        List<TestInput> testInputs = request.tests() == null ? List.of() : request.tests();
        List<SideCaseInput> sideCaseInputs = request.sideCases() == null ? List.of() : request.sideCases();
        sideCases.deleteByExerciseId(exercise.getId());
        tests.deleteByExerciseId(exercise.getId());

        List<ExerciseTest> saved = new ArrayList<>();
        for (TestInput input : testInputs) {
            String visibility = visibility(input.visibility());
            UUID stubId = input.stubId();
            if (stubId != null && stubs.findById(stubId)
                    .filter(stub -> stub.getExerciseId().equals(exercise.getId())).isEmpty()) {
                throw badRequest("Test references a stub that does not belong to this exercise");
            }
            ExerciseTest test = tests.save(new ExerciseTest(UUID.randomUUID(), exercise.getId(),
                    stubId, input.name().trim(), visibility, input.content(), input.timeoutMs()));
            saved.add(test);
        }
        for (SideCaseInput input : sideCaseInputs) {
            String visibility = visibility(input.visibility());
            UUID requestedTestId = input.testId();
            UUID testId = requestedTestId != null
                    && saved.stream().anyMatch(test -> test.getId().equals(requestedTestId))
                    ? requestedTestId
                    : null;
            sideCases.save(new ExerciseSideCase(UUID.randomUUID(), exercise.getId(), testId,
                    input.name().trim(), visibility, json(input.input()), json(input.expected())));
        }
        return saved.stream().map(this::toTestView).toList();
    }

    @Transactional
    public ExerciseDetailView publish(UUID exerciseId, Actor actor) {
        Exercise exercise = requireDraftOwner(exerciseId, actor);
        ExerciseSource source = requireSource(exerciseId);
        List<ExerciseStub> exerciseStubs = stubs.findByExerciseIdOrderByStartLineAsc(exerciseId);
        if (exerciseStubs.isEmpty()) {
            throw badRequest("At least one stub is required before publishing");
        }
        List<ExerciseTest> publicTests = tests.findByExerciseIdAndVisibilityOrderByCreatedAtAsc(
                exerciseId, "PUBLIC");
        if (publicTests.isEmpty()) {
            throw badRequest("At least one public test is required before publishing");
        }
        exercise.publish(taskDocument(exercise, source, exerciseStubs, publicTests));
        exercises.save(exercise);
        return detail(exercise, actor);
    }

    @Transactional
    public ExerciseAcceptance accept(UUID exerciseId, Actor actor, AcceptExerciseRequest request) {
        Exercise exercise = exercises.findById(exerciseId)
                .orElseThrow(() -> notFound(exerciseId));
        if (!exercise.isPublished()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Exercise is not published");
        }
        if (exercise.isSubtask() && actor.id() != null && actor.id().equals(exercise.getCreatedBy())) {
            throw badRequest("You cannot accept a subtask you published");
        }
        Set<String> missing = new LinkedHashSet<>(requiredAgreements);
        missing.removeAll(request.agreements());
        if (!missing.isEmpty()) {
            throw badRequest("Missing required agreements: " + missing);
        }
        String hash = MessageSigner.sha256(exercise.getTaskDocument());
        String message = MessageSigner.canonicalExerciseAcceptMessage(
                exercise.getId(), exercise.getVersion(), hash);
        if (!MessageSigner.verify(request.publicKey(), request.signature(), message)) {
            throw badRequest("Signature does not match the exercise document");
        }
        Instant now = Instant.now();
        ExerciseAcceptance acceptance = new ExerciseAcceptance(UUID.randomUUID(), exercise.getId(),
                actor.workerId(), request.publicKey(), hash, request.signature(),
                json(request.agreements()), true, now, now.plus(exercise.getDeadlineDays(), ChronoUnit.DAYS));
        try {
            return acceptances.saveAndFlush(acceptance);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Worker " + actor.workerId() + " already accepted this exercise");
        }
    }

    @Transactional
    public WorkspaceView workspace(UUID exerciseId, Actor actor) {
        Exercise exercise = exercises.findById(exerciseId)
                .orElseThrow(() -> notFound(exerciseId));
        ExerciseAcceptance acceptance = requireActiveAcceptance(exerciseId, actor);
        ExerciseWorkspaceSession session = sessions.findByAcceptanceId(acceptance.getId())
                .filter(existing -> existing.getExpiresAt().isAfter(Instant.now()))
                .orElseGet(() -> sessions.save(new ExerciseWorkspaceSession(UUID.randomUUID(),
                        exerciseId, acceptance.getId(), actor.workerId(), acceptance.getDeadlineAt())));
        return new WorkspaceView(detail(exercise, actor), session.getId(), acceptance.getId(),
                acceptance.getStatus(), acceptance.getDeadlineAt(), false);
    }

    public StaticCheckResult staticCheck(UUID exerciseId, Actor actor, StaticCheckRequest request) {
        Exercise exercise = exercises.findById(exerciseId)
                .orElseThrow(() -> notFound(exerciseId));
        requireActiveAcceptance(exerciseId, actor);
        return staticChecks.check(exercise, request.content());
    }

    @Transactional
    public SubmissionView submit(UUID exerciseId, Actor actor, SubmissionRequest request) {
        Exercise exercise = exercises.findById(exerciseId)
                .orElseThrow(() -> notFound(exerciseId));
        ExerciseAcceptance acceptance = requireActiveAcceptance(exerciseId, actor);
        StaticCheckResult result = staticChecks.check(exercise, request.content());
        String status = result.passed() ? "SUBMITTED" : "STATIC_FAILED";
        String findings = json(result.findings());
        ExerciseSubmission submission = submissions.findByAcceptanceId(acceptance.getId())
                .orElseGet(() -> new ExerciseSubmission(UUID.randomUUID(), exerciseId,
                        acceptance.getId(), actor.workerId(), request.content(),
                        MessageSigner.sha256(request.content()), status, findings));
        submission.replace(request.content(), MessageSigner.sha256(request.content()), status, findings);
        submissions.save(submission);
        acceptance.markSubmitted();
        acceptances.save(acceptance);
        return toSubmissionView(submission);
    }

    public List<SubmissionView> submissionsForExercise(UUID exerciseId, Actor actor) {
        Exercise exercise = exercises.findById(exerciseId)
                .orElseThrow(() -> notFound(exerciseId));
        if (!canManage(exercise, actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the task author can view submissions");
        }
        return submissions.findByExerciseIdOrderBySubmittedAtDesc(exerciseId).stream()
                .map(this::toSubmissionView)
                .toList();
    }

    public List<ExerciseAcceptanceView> acceptedForWorker(Actor actor) {
        return acceptances.findByWorkerIdOrderByAcceptedAtDesc(actor.workerId()).stream()
                .map(this::toAcceptanceView)
                .toList();
    }

    @Transactional
    public int expireStale() {
        return acceptances.expireStale(Instant.now());
    }

    private ExerciseDetailView detail(Exercise exercise, Actor actor) {
        boolean owner = canManage(exercise, actor);
        ExerciseSource source = sources.findByExerciseId(exercise.getId()).orElse(null);
        List<ExerciseStub> exerciseStubs = stubs.findByExerciseIdOrderByStartLineAsc(exercise.getId());
        List<ExerciseTest> exerciseTests = owner
                ? tests.findByExerciseIdOrderByCreatedAtAsc(exercise.getId())
                : tests.findByExerciseIdAndVisibilityOrderByCreatedAtAsc(exercise.getId(), "PUBLIC");
        List<ExerciseSideCase> exerciseCases = owner
                ? sideCases.findByExerciseIdOrderByCreatedAtAsc(exercise.getId())
                : sideCases.findByExerciseIdAndVisibilityOrderByCreatedAtAsc(exercise.getId(), "PUBLIC");
        SourceView sourceView = source == null ? null
                : importer.view(source, owner, owner ? importer.scanFindings(source.getOriginalContent()) : List.of());
        return new ExerciseDetailView(exercise.getId(), exercise.getTitle(), exercise.getSummary(),
                exercise.getLanguage(), exercise.getStatus(), exercise.getVersion(),
                exercise.getComplexity(), exercise.getPriceCents(), exercise.getCurrency(),
                exercise.getDeadlineDays(), exercise.getTaskDocument(), sourceView,
                exerciseStubs.stream().map(this::toStubView).toList(),
                exerciseTests.stream().map(this::toTestView).toList(),
                exerciseCases.stream().map(this::toSideCaseView).toList(),
                exercise.getParentExerciseId(), exercise.getCreatedBy(),
                exercise.getPublishedAt(), exercise.getCreatedAt());
    }

    private ExerciseView toView(Exercise exercise) {
        int publicTests = tests.findByExerciseIdAndVisibilityOrderByCreatedAtAsc(
                exercise.getId(), "PUBLIC").size();
        int stubCount = stubs.findByExerciseIdOrderByStartLineAsc(exercise.getId()).size();
        return new ExerciseView(exercise.getId(), exercise.getTitle(), exercise.getSummary(),
                exercise.getLanguage(), exercise.getStatus(), exercise.getVersion(),
                exercise.getComplexity(), exercise.getPriceCents(), exercise.getCurrency(),
                exercise.getDeadlineDays(), publicTests, stubCount, exercise.getParentExerciseId(),
                exercise.getPublishedAt(), exercise.getCreatedAt());
    }

    private StubView toStubView(ExerciseStub stub) {
        return new StubView(stub.getId(), stub.getSymbolName(), stub.getSymbolKind(),
                stub.getStartLine(), stub.getEndLine(), stub.getStubContent(), stub.getOriginalHash());
    }

    private ExerciseTestView toTestView(ExerciseTest test) {
        return new ExerciseTestView(test.getId(), test.getStubId(), test.getName(),
                test.getVisibility(), test.getContent(), test.getTimeoutMs());
    }

    private SideCaseView toSideCaseView(ExerciseSideCase sideCase) {
        return new SideCaseView(sideCase.getId(), sideCase.getTestId(), sideCase.getName(),
                sideCase.getVisibility(), readJson(sideCase.getInputJson()),
                readJson(sideCase.getExpectedJson()));
    }

    public ExerciseAcceptanceView acceptanceView(ExerciseAcceptance acceptance) {
        return toAcceptanceView(acceptance);
    }

    private ExerciseAcceptanceView toAcceptanceView(ExerciseAcceptance acceptance) {
        return new ExerciseAcceptanceView(acceptance.getId(), acceptance.getExerciseId(),
                acceptance.getWorkerId(), acceptance.getTaskDocumentHash(), acceptance.isVerified(),
                acceptance.getStatus(), acceptance.getAcceptedAt(), acceptance.getDeadlineAt());
    }

    private SubmissionView toSubmissionView(ExerciseSubmission submission) {
        return new SubmissionView(submission.getId(), submission.getExerciseId(),
                submission.getWorkerId(), submission.getStatus(), submission.getContentHash(),
                readJson(submission.getStaticFindings()), submission.getSubmittedAt());
    }

    private Exercise requireDraftOwner(UUID exerciseId, Actor actor) {
        Exercise exercise = exercises.findById(exerciseId)
                .orElseThrow(() -> notFound(exerciseId));
        if (!canManage(exercise, actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the task author can edit this exercise");
        }
        if (!exercise.isDraft()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Published exercises are immutable");
        }
        return exercise;
    }

    private ExerciseAcceptance requireActiveAcceptance(UUID exerciseId, Actor actor) {
        ExerciseAcceptance acceptance = acceptances.findByExerciseIdAndWorkerId(exerciseId, actor.workerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Accept the exercise before opening the workspace"));
        if (!acceptance.isActiveAt(Instant.now())) {
            acceptance.expire();
            acceptances.save(acceptance);
            throw new ResponseStatusException(HttpStatus.GONE, "The acceptance deadline has passed");
        }
        return acceptance;
    }

    private ExerciseSource requireSource(UUID exerciseId) {
        return sources.findByExerciseId(exerciseId)
                .orElseThrow(() -> badRequest("Import a source file before extracting stubs"));
    }

    private StubExtractor extractor(String language) {
        StubExtractor extractor = extractors.get(language.toLowerCase(Locale.ROOT));
        if (extractor == null) {
            throw badRequest("Stub extraction is not available for " + language + " yet");
        }
        return extractor;
    }

    private static void requireWorker(Actor actor) {
        if (actor == null || !actor.isWorker()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Worker role required to publish a subtask");
        }
    }

    private static void requirePublisher(Actor actor) {
        if (actor == null || (!actor.isPublisher() && !actor.isAdmin())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Publisher role required");
        }
        if (actor.orgId() == null) {
            throw badRequest("Your account is not attached to an organization");
        }
    }

    private static boolean canManage(Exercise exercise, Actor actor) {
        if (actor == null) {
            return false;
        }
        if (actor.isAdmin()) {
            return true;
        }
        if (actor.isPublisher() && actor.orgId() != null
                && actor.orgId().equals(exercise.getOrgId())) {
            return true;
        }
        // A worker owns the subtasks they publish, even though they cannot create
        // top-level publisher tasks.
        return actor.isWorker() && exercise.isSubtask() && actor.id() != null
                && actor.id().equals(exercise.getCreatedBy());
    }

    private static void requireLanguage(String language) {
        if (!"java".equals(language)) {
            throw badRequest("Java is the first supported exercise language");
        }
    }

    private static String visibility(String value) {
        String normalized = value == null ? "" : value.toUpperCase(Locale.ROOT);
        if (!VISIBILITIES.contains(normalized)) {
            throw badRequest("visibility must be PUBLIC or HIDDEN");
        }
        return normalized;
    }

    private String taskDocument(Exercise exercise, ExerciseSource source,
            List<ExerciseStub> exerciseStubs, List<ExerciseTest> publicTests) {
        StringBuilder document = new StringBuilder();
        document.append("# ").append(exercise.getTitle()).append("\n\n");
        document.append(exercise.getSummary()).append("\n\n");
        document.append("- Language: ").append(exercise.getLanguage()).append("\n");
        document.append("- Complexity: ").append(exercise.getComplexity()).append("\n");
        document.append("- Price tier: ").append(priceTier(exercise.getComplexity())).append("\n");
        document.append("- Deadline: ").append(exercise.getDeadlineDays()).append(" days after acceptance\n");
        if (exercise.isSubtask()) {
            document.append("- Parent task: ").append(exercise.getParentExerciseId()).append("\n");
        }
        document.append("- Source: ").append(source.getRepoUrl()).append(" @ ")
                .append(source.getCommitSha(), 0, 12).append("\n\n");
        document.append("## Implement\n\n");
        for (ExerciseStub stub : exerciseStubs) {
            document.append("- `").append(stub.getSymbolName()).append("` (")
                    .append(stub.getSymbolKind().toLowerCase(Locale.ROOT)).append(", lines ")
                    .append(stub.getStartLine()).append("-").append(stub.getEndLine()).append(")\n");
        }
        document.append("\n## Public tests\n\n");
        for (ExerciseTest test : publicTests) {
            document.append("- ").append(test.getName()).append(": ").append(test.getContent()).append("\n");
        }
        document.append("\nThe workspace records your submission but does not execute code in this version.\n");
        return document.toString();
    }

    /** The public board shows only a symbolic tier derived from complexity. */
    public static String priceTier(String complexity) {
        return switch (complexity == null ? "" : complexity.toUpperCase(Locale.ROOT)) {
            case "MEDIUM" -> "$$";
            case "HARD" -> "$$$";
            case "EXPERT" -> "$$$$";
            default -> "$";
        };
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (RuntimeException e) {
            throw badRequest("Value is not valid JSON");
        }
    }

    private JsonNode readJson(String value) {
        try {
            return mapper.readTree(value);
        } catch (RuntimeException e) {
            return mapper.createArrayNode();
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException notFound(UUID id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise " + id + " not found");
    }
}
