package io.symmetry.contract;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
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
import io.symmetry.task.WorkKind;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ContractService {

    private static final Set<String> TASK_KINDS = Set.of("optimize", "build");
    private static final Set<String> DISCLOSURE_TIERS = Set.of("L0", "L1", "L2");

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
                workKind, request.taskDocument(), writeJson(request.payload()));
        try {
            return toView(contracts.save(contract));
        } catch (DataIntegrityViolationException e) {
            // Lost a race: another request inserted the same module@version first.
            throw conflict(request.moduleId(), request.version());
        }
    }

    public PageResponse<ContractView> list(String moduleId, Pageable pageable) {
        Page<Contract> page = (moduleId == null || moduleId.isBlank())
                ? contracts.findAll(pageable)
                : contracts.findByModuleId(moduleId, pageable);
        return PageResponse.from(page, this::toView);
    }

    public ContractView get(String moduleId, String version) {
        return contracts.findByModuleIdAndVersion(moduleId, version)
                .map(this::toView)
                .orElseThrow(() -> notFound(moduleId, version));
    }

    public WorkbenchView workbench(String moduleId, String version) {
        Contract contract = contracts.findByModuleIdAndVersion(moduleId, version)
                .orElseThrow(() -> notFound(moduleId, version));
        return disclosure.workbench(toView(contract));
    }

    @Transactional
    public Acceptance accept(UUID contractId, AcceptRequest request) {
        Contract contract = contracts.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Contract " + contractId + " not found"));

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

    private ContractView toView(Contract contract) {
        return new ContractView(contract.getId(), contract.getModuleId(), contract.getVersion(),
                contract.getTaskKind(), contract.getLanguage(), contract.getWorkKind().name().toLowerCase(),
                contract.getLifecycleState().name().toLowerCase(), contract.getDisclosureTier(),
                contract.getTaskDocument(), readJson(contract.getPayload()), contract.getCreatedAt());
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
