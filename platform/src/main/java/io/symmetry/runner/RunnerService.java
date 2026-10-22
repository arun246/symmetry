package io.symmetry.runner;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import io.symmetry.audit.AuditService;
import io.symmetry.contract.Contract;
import io.symmetry.contract.ContractRepository;
import io.symmetry.contract.MessageSigner;
import io.symmetry.events.EventService;
import io.symmetry.identity.Actor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class RunnerService {

    private static final String CHANNEL = "runners";

    private final RunnerRepository runners;
    private final JobLeaseRepository leases;
    private final RunnerResultRepository results;
    private final ContractRepository contracts;
    private final RunnerAuthService auth;
    private final AuditService audit;
    private final EventService events;
    private final ObjectMapper mapper;
    private final long leaseSeconds;

    public RunnerService(RunnerRepository runners, JobLeaseRepository leases,
            RunnerResultRepository results, ContractRepository contracts,
            RunnerAuthService auth, AuditService audit, EventService events, ObjectMapper mapper,
            @Value("${symmetry.runner.lease-seconds:600}") long leaseSeconds) {
        if (leaseSeconds < 1 || leaseSeconds > 3600) {
            throw new IllegalArgumentException("symmetry.runner.lease-seconds must be 1..3600");
        }
        this.runners = runners;
        this.leases = leases;
        this.results = results;
        this.contracts = contracts;
        this.auth = auth;
        this.audit = audit;
        this.events = events;
        this.mapper = mapper;
        this.leaseSeconds = leaseSeconds;
    }

    @Transactional
    public RegisteredRunnerView register(RegisterRunnerRequest request) {
        auth.requireRegistrationToken(request.registrationToken());
        if (!MessageSigner.validPublicKey(request.publicKey())) {
            throw badRequest("publicKey must be a base64 X.509 Ed25519 public key");
        }

        String capabilities = objectJson(request.capabilities(), "capabilities");
        RunnerAuthService.IssuedToken token = RunnerAuthService.issueToken();
        Runner runner = new Runner(UUID.randomUUID(), request.name().trim(),
                request.hostname().trim(), null, request.publicKey().trim(), token.hash(),
                request.version().trim(), capabilities);
        try {
            runners.saveAndFlush(runner);
        } catch (DataIntegrityViolationException race) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A runner with this public key or token already exists");
        }

        audit.record(Actor.system(), "runner.register", "runner", runner.getId().toString(), null,
                Map.of("name", runner.getName(), "hostname", runner.getHostname(),
                        "version", runner.getVersion()));
        events.publish(CHANNEL, "runner.registered", Map.of(
                "id", runner.getId().toString(),
                "name", runner.getName(),
                "hostname", runner.getHostname()));
        return new RegisteredRunnerView(runner.getId(), token.raw(),
                runner.getStatus().name().toLowerCase(), runner.getRegisteredAt(), leaseSeconds);
    }

    @Transactional
    public HeartbeatView heartbeat(UUID runnerId, String authorization, HeartbeatRequest request) {
        Runner runner = auth.authenticate(runnerId, authorization);
        Instant now = Instant.now();
        String version = runner.getVersion();
        if (request != null && request.version() != null && !request.version().isBlank()) {
            version = request.version().trim();
        }
        String capabilities = runner.getCapabilities();
        if (request != null && request.capabilities() != null) {
            capabilities = objectJson(request.capabilities(), "capabilities");
        }
        runner.heartbeat(now, version, capabilities);
        return new HeartbeatView(runner.getId(), runner.getStatus().name().toLowerCase(),
                now, now, leaseSeconds);
    }

    /**
     * Lease at most one accepted contract that is not already leased or covered
     * by a live result. Expired leases are released first, so a dead host is
     * retried rather than blocking the work forever.
     */
    @Transactional
    public Optional<LeaseView> lease(UUID runnerId, String authorization) {
        Runner runner = auth.authenticate(runnerId, authorization);
        Instant now = Instant.now();
        leases.expireStale(now);

        List<Contract> candidates = contracts.findLeasable(PageRequest.of(0, 1));
        runner.heartbeat(now, runner.getVersion(), runner.getCapabilities());
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        Contract contract = candidates.get(0);
        JobLease lease = new JobLease(UUID.randomUUID(), runner.getId(), contract.getId(),
                RunnerAuthService.newNonce(), now, now.plusSeconds(leaseSeconds));
        try {
            leases.saveAndFlush(lease);
        } catch (DataIntegrityViolationException race) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Contract " + contract.getId() + " was leased by another runner");
        }

        events.publish(CHANNEL, "job.leased", Map.of(
                "leaseId", lease.getId().toString(),
                "runnerId", runner.getId().toString(),
                "contractId", contract.getId().toString()));
        return Optional.of(new LeaseView(lease.getId(), contract.getId(), contract.getModuleId(),
                contract.getVersion(), contract.getLanguage(),
                MessageSigner.sha256(contract.getTaskDocument()), lease.getNonce(),
                lease.getLeasedAt(), lease.getExpiresAt()));
    }

    @Transactional
    public ResultView submitResult(UUID runnerId, String authorization, SubmitResultRequest request) {
        Runner runner = auth.authenticate(runnerId, authorization);
        JobLease lease = leases.findByIdAndRunnerId(request.leaseId(), runnerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Lease " + request.leaseId() + " not found for this runner"));

        String payloadHash = MessageSigner.sha256(request.payload());
        RunnerResult existing = results.findByLeaseId(lease.getId()).orElse(null);
        if (existing != null) {
            if (existing.getPayloadHash().equals(payloadHash)
                    && existing.getSignature().equals(request.signature())) {
                return toView(existing);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A different result was already submitted for this lease");
        }

        if (!lease.getNonce().equals(request.nonce())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lease nonce does not match");
        }
        if (!lease.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Lease is " + lease.getStatus().name().toLowerCase());
        }
        Instant now = Instant.now();
        if (lease.isExpiredAt(now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Lease expired at " + lease.getExpiresAt());
        }

        String message = MessageSigner.canonicalResultMessage(lease.getId(), lease.getNonce(),
                lease.getContractId(), payloadHash);
        if (!MessageSigner.verify(runner.getPublicKey(), request.signature(), message)) {
            throw badRequest("Result signature does not verify with the runner public key");
        }

        ParsedResult parsed = parse(request.payload());
        RunnerResult result = new RunnerResult(UUID.randomUUID(), lease.getId(), runner.getId(),
                lease.getContractId(), lease.getNonce(), request.payload(), payloadHash,
                request.signature(), parsed.passed(), parsed.artifactDigest(), parsed.logsDigest());
        try {
            results.saveAndFlush(result);
        } catch (DataIntegrityViolationException race) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A result was already submitted for this lease");
        }
        lease.complete(now);
        runner.heartbeat(now, runner.getVersion(), runner.getCapabilities());

        audit.record(Actor.system(), "runner.result", "runner_result", result.getId().toString(),
                null, Map.of("leaseId", lease.getId().toString(),
                        "contractId", lease.getContractId().toString(),
                        "passed", result.isPassed(), "payloadHash", payloadHash));
        events.publish(CHANNEL, "job.result", Map.of(
                "leaseId", lease.getId().toString(),
                "contractId", lease.getContractId().toString(),
                "passed", result.isPassed()));
        return toView(result);
    }

    @Transactional
    public RunnerView revoke(UUID runnerId, Actor actor) {
        Runner runner = runners.findById(runnerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Runner " + runnerId + " not found"));
        if (runner.isActive()) {
            Instant now = Instant.now();
            runner.revoke(now);
            leases.revokeActiveForRunner(runnerId);
            results.quarantineForRunner(runnerId);
            audit.record(actor, "runner.revoke", "runner", runnerId.toString(),
                    Map.of("status", "active"), Map.of("status", "revoked"));
            events.publish(CHANNEL, "runner.revoked", Map.of("id", runnerId.toString()));
        }
        return toView(runner);
    }

    @Transactional(readOnly = true)
    public List<RunnerView> list() {
        return runners.findAll(Sort.by(Sort.Direction.DESC, "registeredAt")).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public RunnerView get(UUID runnerId) {
        return runners.findById(runnerId)
                .map(this::toView)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Runner " + runnerId + " not found"));
    }

    private ParsedResult parse(String payload) {
        if (payload.length() > 65_536) {
            throw badRequest("payload is larger than 64 KiB");
        }
        JsonNode node;
        try {
            node = mapper.readTree(payload);
        } catch (JacksonException e) {
            throw badRequest("payload must be valid JSON");
        }
        if (node == null || !node.isObject()) {
            throw badRequest("payload must be a JSON object");
        }
        JsonNode passed = node.get("passed");
        if (passed == null || !passed.isBoolean()) {
            throw badRequest("payload.passed must be a boolean");
        }
        JsonNode metrics = node.get("metrics");
        if (metrics != null && !metrics.isNull() && !metrics.isObject()) {
            throw badRequest("payload.metrics must be an object");
        }
        String failedChecker = text(node, "failedChecker", 200);
        String message = text(node, "sanitizedMessage", 4000);
        String artifactDigest = digest(node, "artifactDigest");
        String logsDigest = digest(node, "logsDigest");
        if (passed.booleanValue() && failedChecker != null) {
            throw badRequest("payload.failedChecker must be null when passed is true");
        }
        return new ParsedResult(passed.booleanValue(), message, artifactDigest, logsDigest);
    }

    private static String text(JsonNode node, String field, int maxLength) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw badRequest("payload." + field + " must be a string");
        }
        String text = value.stringValue();
        if (text.length() > maxLength) {
            throw badRequest("payload." + field + " is longer than " + maxLength + " characters");
        }
        return text;
    }

    private static String digest(JsonNode node, String field) {
        String value = text(node, field, 128);
        if (value != null && !value.matches("[0-9a-fA-F]{64}")) {
            throw badRequest("payload." + field + " must be a SHA-256 hex digest");
        }
        return value;
    }

    private String objectJson(JsonNode value, String field) {
        if (value == null || value.isNull()) {
            return "{}";
        }
        if (!value.isObject()) {
            throw badRequest(field + " must be a JSON object");
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw badRequest(field + " is not valid JSON");
        }
    }

    private RunnerView toView(Runner runner) {
        return new RunnerView(runner.getId(), runner.getName(), runner.getHostname(),
                runner.getOrgId(), runner.getPublicKey(), runner.getVersion(),
                readJson(runner.getCapabilities()), runner.getStatus().name().toLowerCase(),
                runner.getLastSeenAt(), runner.getRegisteredAt(), runner.getRevokedAt());
    }

    private ResultView toView(RunnerResult result) {
        return new ResultView(result.getId(), result.getLeaseId(), result.getContractId(),
                result.isPassed(), readJson(result.getPayload()), result.getPayloadHash(),
                result.isQuarantined(), result.getReceivedAt());
    }

    private JsonNode readJson(String value) {
        try {
            return mapper.readTree(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Stored JSON is invalid", e);
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record ParsedResult(boolean passed, String sanitizedMessage,
            String artifactDigest, String logsDigest) {
    }
}
