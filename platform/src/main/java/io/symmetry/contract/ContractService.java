package io.symmetry.contract;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import io.symmetry.build.ToolchainRegistry;
import io.symmetry.identity.Actor;
import io.symmetry.task.WorkKind;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ContractService {

    private static final Set<String> TASK_KINDS = Set.of("optimize", "build");
    private static final Set<String> DISCLOSURE_TIERS = Set.of("L0", "L1", "L2");

    /** Reason codes a worker may give when nullifying an acceptance. */
    public static final List<String> WITHDRAWAL_REASONS = List.of(
            "task-unclear",
            "requirements-changed",
            "cannot-test",
            "not-enough-time",
            "outside-skills",
            "other");
    private static final int MAX_WITHDRAWAL_COMMENT = 2000;

    private final ContractRepository contracts;
    private final AcceptanceRepository acceptances;
    private final ToolchainRegistry registry;
    private final ContractDocumentValidator validator;
    private final DisclosureService disclosure;
    private final ObjectMapper mapper;
    private final Set<String> requiredAgreements;

    public ContractService(
            ContractRepository contracts,
            AcceptanceRepository acceptances,
            ToolchainRegistry registry,
            ContractDocumentValidator validator,
            DisclosureService disclosure,
            ObjectMapper mapper,
            @Value("${symmetry.acceptance.required-agreements:honesty,scope,ai-declaration,confidentiality,responsibility,license}")
            String requiredAgreements) {
        this.contracts = contracts;
        this.acceptances = acceptances;
        this.registry = registry;
        this.validator = validator;
        this.disclosure = disclosure;
        this.mapper = mapper;
        this.requiredAgreements = Arrays.stream(requiredAgreements.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public ContractView create(ContractRequest request) {
        return create(request, Actor.system());
    }

    public ContractView create(ContractRequest request, Actor actor) {
        if (!TASK_KINDS.contains(request.taskKind())) {
            throw badRequest("taskKind must be one of " + TASK_KINDS);
        }
        if (!DISCLOSURE_TIERS.contains(request.disclosureTier())) {
            throw badRequest("disclosureTier must be one of " + DISCLOSURE_TIERS);
        }
        if (!registry.supports(request.language())) {
            throw badRequest("Unsupported language '" + request.language()
                    + "'. Known languages: " + registry.supported());
        }
        WorkKind workKind = parseWorkKind(request.workKind());

        List<String> errors = validator.validate(request.payload(), request.taskDocument());
        if (!errors.isEmpty()) {
            throw badRequest("Invalid contract document: " + String.join("; ", errors));
        }
        if (contracts.findByModuleIdAndVersion(request.moduleId(), request.version()).isPresent()) {
            throw conflict(request.moduleId(), request.version());
        }

        Contract contract = new Contract(UUID.randomUUID(), request.moduleId(), request.version(),
                request.taskKind(), request.language().toLowerCase(), request.disclosureTier(),
                workKind, request.taskDocument(), writeJson(request.payload()),
                actor == null ? null : actor.orgId(), actor == null ? null : actor.id());
        try {
            return toView(contracts.save(contract));
        } catch (DataIntegrityViolationException e) {
            // Lost a race: another request inserted the same module@version first.
            throw conflict(request.moduleId(), request.version());
        }
    }

    public PageResponse<ContractView> list(String moduleId, Pageable pageable) {
        // Drafts are private authoring state. The public registry only exposes
        // contracts that have been published for workers.
        Page<Contract> page = (moduleId == null || moduleId.isBlank())
                ? contracts.findByLifecycleStateNot(io.symmetry.task.LifecycleState.DRAFT, pageable)
                : contracts.findByModuleIdAndLifecycleStateNot(moduleId,
                        io.symmetry.task.LifecycleState.DRAFT, pageable);
        return PageResponse.from(page, this::toView);
    }

    public PageResponse<ContractView> listForOrg(UUID orgId, Pageable pageable) {
        return PageResponse.from(contracts.findByOrgId(orgId, pageable), this::toView);
    }

    public ContractView get(String moduleId, String version) {
        return contracts.findByModuleIdAndVersion(moduleId, version)
                .map(this::toView)
                .orElseThrow(() -> notFound(moduleId, version));
    }

    @Transactional
    public ContractView publish(UUID contractId, Actor actor) {
        Contract contract = contracts.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Contract " + contractId + " not found"));
        if (actor == null || (!actor.isAdmin() && !(actor.isPublisher()
                && actor.orgId() != null && actor.orgId().equals(contract.getOrgId())))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the owning publisher can publish this contract");
        }
        if (contract.getLifecycleState() != io.symmetry.task.LifecycleState.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Contract is already published");
        }
        contract.publish();
        return toView(contracts.save(contract));
    }

    public WorkbenchView workbench(String moduleId, String version) {
        Contract contract = contracts.findByModuleIdAndVersion(moduleId, version)
                .orElseThrow(() -> notFound(moduleId, version));
        if (contract.getLifecycleState() == io.symmetry.task.LifecycleState.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Contract is not published yet");
        }
        return disclosure.workbench(toView(contract));
    }

    @Transactional
    public Acceptance accept(UUID contractId, AcceptRequest request) {
        Contract contract = contracts.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Contract " + contractId + " not found"));
        if (contract.getLifecycleState() == io.symmetry.task.LifecycleState.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Contract is not published yet");
        }

        Set<String> missing = new LinkedHashSet<>(requiredAgreements);
        missing.removeAll(request.agreements());
        if (!missing.isEmpty()) {
            throw badRequest("Missing required agreements: " + missing);
        }

        String hash = MessageSigner.sha256(contract.getTaskDocument());
        String message = MessageSigner.canonicalAcceptMessage(
                contract.getModuleId(), contract.getVersion(), hash);
        if (!MessageSigner.verify(request.publicKey(), request.signature(), message)) {
            throw badRequest("Signature does not match the task document");
        }

        Acceptance acceptance = new Acceptance(UUID.randomUUID(), contract.getId(), request.workerId(),
                hash, request.signature(), request.publicKey(), true, writeJson(request.agreements()));
        contract.markAccepted();
        contracts.save(contract);
        try {
            // Flush now so the unique (contract_id, worker_id) constraint is checked
            // inside this transaction and can be reported as 409, not a 500.
            return acceptances.saveAndFlush(acceptance);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Worker " + request.workerId() + " already accepted contract " + contractId);
        }
    }

    @Transactional
    public WithdrawalView withdraw(UUID contractId, WithdrawRequest request, Actor actor) {
        if (actor == null || !actor.isWorker()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only a worker can nullify an acceptance");
        }
        Acceptance acceptance = acceptances.findByContractIdAndWorkerId(contractId, actor.workerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No acceptance of contract " + contractId + " for " + actor.workerId()));
        if (acceptance.isWithdrawn()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Acceptance is already nullified");
        }

        List<String> reasons = request.reasons() == null ? List.of() : request.reasons().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(reason -> !reason.isEmpty())
                .distinct()
                .toList();
        if (reasons.isEmpty()) {
            throw badRequest("Choose at least one reason");
        }
        List<String> unknown = reasons.stream()
                .filter(reason -> !WITHDRAWAL_REASONS.contains(reason))
                .toList();
        if (!unknown.isEmpty()) {
            throw badRequest("Unknown reason(s): " + unknown);
        }
        String comment = request.comment() == null ? null : request.comment().trim();
        if (comment != null && comment.isEmpty()) {
            comment = null;
        }
        if (comment != null && comment.length() > MAX_WITHDRAWAL_COMMENT) {
            throw badRequest("Comment is longer than " + MAX_WITHDRAWAL_COMMENT + " characters");
        }
        if (reasons.contains("other") && comment == null) {
            throw badRequest("Describe the reason when 'other' is selected");
        }

        acceptance.withdraw(writeJson(reasons), comment);
        Acceptance saved = acceptances.save(acceptance);
        return new WithdrawalView(saved.getId(), contractId, saved.getStatus(),
                readStringArray(saved.getWithdrawalReasons()), saved.getWithdrawalComment(),
                saved.getWithdrawnAt());
    }

    private ContractView toView(Contract contract) {
        return new ContractView(contract.getId(), contract.getModuleId(), contract.getVersion(),
                contract.getTaskKind(), contract.getLanguage(), contract.getWorkKind().name().toLowerCase(),
                contract.getLifecycleState().name().toLowerCase(), contract.getDisclosureTier(),
                contract.getTaskDocument(), readJson(contract.getPayload()),
                contract.getOrgId(), contract.getCreatedBy(), contract.getCreatedAt());
    }

    private static WorkKind parseWorkKind(String value) {
        try {
            return WorkKind.parse(value);
        } catch (IllegalArgumentException e) {
            throw badRequest("workKind must be one of " + Arrays.toString(WorkKind.values()));
        }
    }

    private JsonNode readJson(String value) {
        try {
            return mapper.readTree(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Stored contract payload is not valid JSON", e);
        }
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw badRequest("payload is not valid JSON");
        }
    }

    private List<String> readStringArray(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            List<String> values = new ArrayList<>();
            for (JsonNode item : mapper.readTree(json)) {
                values.add(item.asText());
            }
            return List.copyOf(values);
        } catch (JacksonException e) {
            throw new IllegalStateException("Stored withdrawal reasons are not valid JSON", e);
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String moduleId, String version) {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "Contract " + moduleId + "@" + version + " already exists");
    }

    private static ResponseStatusException notFound(String moduleId, String version) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Contract " + moduleId + "@" + version + " not found");
    }
}
