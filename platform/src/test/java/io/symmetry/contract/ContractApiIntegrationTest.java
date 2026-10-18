package io.symmetry.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration tests: the real Spring context, the real HTTP layer, and a real
 * PostgreSQL (so Flyway migrations and JPA mappings are exercised). Skipped when
 * Docker is unavailable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ContractApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    private String contractBody(String moduleId, String version, String tier, String workKind,
            boolean withSecrets) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("signature", Map.of("name", "resize"));
        payload.put("invariants", List.of("dims_match", "alpha_preserved"));
        payload.put("resourceBudget", Map.of("timeMs", 12, "peakMemMb", 8, "artifactKb", 64));
        Map<String, Object> oracle = new LinkedHashMap<>();
        oracle.put("holdout", "secret");
        oracle.put("properties", "ref");
        payload.put("oracle", oracle);
        payload.put("scoring", Map.of("gate", "all_pass"));
        if (withSecrets) {
            payload.put("samples", List.of(1, 2));
            payload.put("realExamples", List.of(3));
            payload.put("holdout", "top-secret");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("moduleId", moduleId);
        body.put("version", version);
        body.put("taskKind", "optimize");
        body.put("language", "java");
        body.put("disclosureTier", tier);
        if (workKind != null) {
            body.put("workKind", workKind);
        }
        body.put("taskDocument", taskDocument(moduleId));
        body.put("payload", payload);
        return mapper.writeValueAsString(body);
    }

    private static String taskDocument(String moduleId) {
        return "task document for " + moduleId;
    }

    private String publish(String moduleId, String version, String tier) throws Exception {
        return mockMvc.perform(post("/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contractBody(moduleId, version, tier, null, true)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void publishesAndFetchesAContract() throws Exception {
        String moduleId = "module.image.resize.it.publish";

        mockMvc.perform(post("/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contractBody(moduleId, "1.0.0", "L1", "compute", false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.moduleId").value(moduleId))
                .andExpect(jsonPath("$.language").value("java"))
                .andExpect(jsonPath("$.workKind").value("compute"))
                .andExpect(jsonPath("$.lifecycleState").value("draft"));

        mockMvc.perform(get("/contracts/{moduleId}/{version}", moduleId, "1.0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskKind").value("optimize"))
                .andExpect(jsonPath("$.disclosureTier").value("L1"));

        mockMvc.perform(get("/contracts").param("moduleId", moduleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].moduleId").value(moduleId));
    }

    @Test
    void workbenchHidesSecretsAccordingToTier() throws Exception {
        String l2 = "module.image.resize.it.l2";
        publish(l2, "1.0.0", "L2");

        mockMvc.perform(get("/contracts/{moduleId}/{version}/workbench", l2, "1.0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.holdout").doesNotExist())
                .andExpect(jsonPath("$.payload.oracle.holdout").doesNotExist())
                .andExpect(jsonPath("$.payload.samples").exists())
                .andExpect(jsonPath("$.payload.realExamples").exists());

        String l0 = "module.image.resize.it.l0";
        publish(l0, "1.0.0", "L0");

        mockMvc.perform(get("/contracts/{moduleId}/{version}/workbench", l0, "1.0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.samples").doesNotExist())
                .andExpect(jsonPath("$.payload.realExamples").doesNotExist())
                .andExpect(jsonPath("$.payload.signature").exists());
    }

    @Test
    void duplicatePublishReturnsConflict() throws Exception {
        String moduleId = "module.image.resize.it.duplicate";
        publish(moduleId, "1.0.0", "L1");

        mockMvc.perform(post("/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contractBody(moduleId, "1.0.0", "L1", null, false)))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidPayloadReturnsBadRequest() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("moduleId", "module.image.resize.it.bad");
        body.put("version", "1.0.0");
        body.put("taskKind", "optimize");
        body.put("language", "java");
        body.put("disclosureTier", "L1");
        body.put("taskDocument", "task");
        body.put("payload", Map.of("signature", Map.of()));  // missing invariants/budget/...

        mockMvc.perform(post("/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownLanguageReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contractBody("m", "1.0.0", "L1", null, false)
                                .replace("\"java\"", "\"cobol\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptIsVerifiedAndAdvancesLifecycle() throws Exception {
        String moduleId = "module.image.resize.it.accept";
        String version = "1.0.0";
        JsonNode created = mapper.readTree(publish(moduleId, version, "L1"));
        String id = created.get("id").asText();

        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String hash = MessageSigner.sha256(taskDocument(moduleId));
        String message = MessageSigner.canonicalAcceptMessage(moduleId, version, hash);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(message.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> accept = new LinkedHashMap<>();
        accept.put("workerId", "worker-42");
        accept.put("agreements", List.of("honesty", "scope", "ai-declaration",
                "confidentiality", "responsibility", "license"));
        accept.put("publicKey", Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        accept.put("signature", Base64.getEncoder().encodeToString(signer.sign()));

        mockMvc.perform(post("/contracts/{id}/accept", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(accept)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.taskDocumentHash").value(hash))
                .andExpect(jsonPath("$.workerId").value("worker-42"));

        String lifecycle = mockMvc.perform(get("/contracts/{moduleId}/{version}", moduleId, version))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(lifecycle).get("lifecycleState").asText()).isEqualTo("accepted");
    }
}
