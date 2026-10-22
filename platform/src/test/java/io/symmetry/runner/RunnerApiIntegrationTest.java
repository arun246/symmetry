package io.symmetry.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.symmetry.contract.MessageSigner;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "symmetry.runner.registration-token=test-registration-token",
        "symmetry.runner.lease-seconds=120"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RunnerApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JobLeaseRepository leases;

    @Autowired
    private RunnerResultRepository results;

    @Test
    void registersHeartbeatsLeasesAndAcceptsAnIdempotentSignedResult() throws Exception {
        publishAndAccept("runner.it.main." + UUID.randomUUID());
        Registered runner = register("runner-main", "test-registration-token");

        mockMvc.perform(post("/runners/{id}/heartbeat", runner.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + runner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.lastSeenAt").exists())
                .andExpect(jsonPath("$.leaseSeconds").value(120));

        JsonNode lease = lease(runner);
        String payload = resultPayload(true, "all checkpoints passed");

        String signature = signResult(runner.keys(), lease.get("leaseId").asText(),
                lease.get("nonce").asText(), lease.get("contractId").asText(), payload);

        JsonNode first = submit(runner, lease, payload, signature)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.payload.sanitizedMessage")
                        .value("all checkpoints passed"))
                .andReturn().getResponse().getContentAsString()
                .transform(this::read);

        JsonNode retry = submit(runner, lease, payload, signature)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true))
                .andReturn().getResponse().getContentAsString()
                .transform(this::read);

        assertThat(retry.get("id").asText()).isEqualTo(first.get("id").asText());
    }

    @Test
    void rejectsATamperedResultButKeepsTheLeaseUsable() throws Exception {
        publishAndAccept("runner.it.tamper." + UUID.randomUUID());
        Registered runner = register("runner-tamper", "test-registration-token");
        JsonNode lease = lease(runner);
        String payload = resultPayload(true, "all checkpoints passed");

        String good = signResult(runner.keys(), lease.get("leaseId").asText(),
                lease.get("nonce").asText(), lease.get("contractId").asText(), payload);
        String tampered = good.substring(0, good.length() - 4) + "AAAA";

        submit(runner, lease, payload, tampered)
                .andExpect(status().isBadRequest());

        submit(runner, lease, payload, good)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true));
    }

    @Test
    void revocationRevokesAnActiveLeaseAndQuarantinesResults() throws Exception {
        publishAndAccept("runner.it.revoke.a." + UUID.randomUUID());
        publishAndAccept("runner.it.revoke.b." + UUID.randomUUID());
        Registered runner = register("runner-revoke", "test-registration-token");

        JsonNode completedLease = lease(runner);
        String payload = resultPayload(true, "all checkpoints passed");
        String signature = signResult(runner.keys(), completedLease.get("leaseId").asText(),
                completedLease.get("nonce").asText(), completedLease.get("contractId").asText(),
                payload);
        submit(runner, completedLease, payload, signature).andExpect(status().isOk());

        JsonNode activeLease = lease(runner);
        assertThat(activeLease.get("leaseId").asText())
                .isNotEqualTo(completedLease.get("leaseId").asText());

        mockMvc.perform(post("/runners/{id}/revoke", runner.id())
                        .header("X-Symmetry-User", "admin@symmetry.local"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("revoked"))
                .andExpect(jsonPath("$.revokedAt").exists());

        mockMvc.perform(post("/runners/{id}/heartbeat", runner.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + runner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        JobLease completed = leases.findById(UUID.fromString(
                completedLease.get("leaseId").asText())).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(LeaseStatus.COMPLETED);
        JobLease active = leases.findById(UUID.fromString(
                activeLease.get("leaseId").asText())).orElseThrow();
        assertThat(active.getStatus()).isEqualTo(LeaseStatus.REVOKED);

        RunnerResult result = results.findByLeaseId(completed.getId()).orElseThrow();
        assertThat(result.isQuarantined()).isTrue();
        assertThat(results.existsByContractIdAndQuarantinedFalse(result.getContractId()))
                .isFalse();
    }

    @Test
    void registrationRequiresTheConfiguredToken() throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("registrationToken", "wrong-token");
        body.put("name", "runner-bad-token");
        body.put("hostname", "bad-token.example");
        body.put("version", "0.0.1");
        body.put("publicKey", Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()));
        body.put("capabilities", Map.of("kvm", true));

        mockMvc.perform(post("/runners/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    private void publishAndAccept(String moduleId) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("signature", Map.of("name", "resize"));
        payload.put("invariants", List.of("dims_match", "alpha_preserved"));
        payload.put("resourceBudget", Map.of("timeMs", 12, "peakMemMb", 8, "artifactKb", 64));
        payload.put("oracle", Map.of("holdout", "secret", "properties", "ref"));
        payload.put("scoring", Map.of("gate", "all_pass"));
        payload.put("samples", List.of(1, 2));

        String taskDocument = "task document for " + moduleId;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("moduleId", moduleId);
        body.put("version", "1.0.0");
        body.put("taskKind", "optimize");
        body.put("language", "java");
        body.put("disclosureTier", "L1");
        body.put("taskDocument", taskDocument);
        body.put("payload", payload);

        String created = mockMvc.perform(post("/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String contractId = read(created).get("id").asText();

        KeyPair worker = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String hash = MessageSigner.sha256(taskDocument);
        String message = MessageSigner.canonicalAcceptMessage(moduleId, "1.0.0", hash);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(worker.getPrivate());
        signer.update(message.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> accept = new LinkedHashMap<>();
        accept.put("workerId", "worker-runner-it");
        accept.put("agreements", List.of("honesty", "scope", "ai-declaration",
                "confidentiality", "responsibility", "license"));
        accept.put("publicKey", Base64.getEncoder().encodeToString(worker.getPublic().getEncoded()));
        accept.put("signature", Base64.getEncoder().encodeToString(signer.sign()));

        mockMvc.perform(post("/contracts/{id}/accept", contractId)
                        .header("X-Symmetry-User", "worker@symmetry.local")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(accept)))
                .andExpect(status().isCreated());
    }

    private Registered register(String name, String registrationToken) throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("registrationToken", registrationToken);
        body.put("name", name);
        body.put("hostname", name + ".example");
        body.put("version", "0.0.1");
        body.put("publicKey", Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()));
        body.put("capabilities", Map.of("kvm", true, "firecracker", true, "arch", "x86-64"));

        String response = mockMvc.perform(post("/runners/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = read(response);
        return new Registered(UUID.fromString(json.get("id").asText()),
                json.get("accessToken").asText(), keys);
    }

    private JsonNode lease(Registered runner) throws Exception {
        String response = mockMvc.perform(post("/runners/{id}/lease", runner.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + runner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaseId").exists())
                .andExpect(jsonPath("$.contractId").exists())
                .andExpect(jsonPath("$.nonce").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn().getResponse().getContentAsString();
        return read(response);
    }

    private org.springframework.test.web.servlet.ResultActions submit(Registered runner,
            JsonNode lease, String payload, String signature) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("leaseId", lease.get("leaseId").asText());
        body.put("nonce", lease.get("nonce").asText());
        body.put("payload", payload);
        body.put("signature", signature);
        return mockMvc.perform(post("/runners/{id}/results", runner.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + runner.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)));
    }

    private String resultPayload(boolean passed, String message) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("passed", passed);
        payload.put("failedChecker", null);
        payload.put("sanitizedMessage", message);
        payload.put("metrics", Map.of("p50", 1.25, "peakMemMb", 8));
        payload.put("artifactDigest", MessageSigner.sha256("artifact"));
        payload.put("logsDigest", MessageSigner.sha256("logs"));
        return mapper.writeValueAsString(payload);
    }

    private String signResult(KeyPair keys, String leaseId, String nonce,
            String contractId, String payload) throws Exception {
        String message = MessageSigner.canonicalResultMessage(UUID.fromString(leaseId), nonce,
                UUID.fromString(contractId), MessageSigner.sha256(payload));
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keys.getPrivate());
        signer.update(message.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    private JsonNode read(String json) {
        return mapper.readTree(json);
    }

    private record Registered(UUID id, String token, KeyPair keys) {
    }
}
