package io.symmetry.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.symmetry.contract.MessageSigner;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end test: the whole journey across a real HTTP server and a real
 * PostgreSQL — publish → browse → read the tier-filtered workbench → sign and
 * accept → confirm the lifecycle advanced. A wrong signature must be refused.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class AcceptanceJourneyE2ETest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String MODULE = "module.image.resize.e2e";
    private static final String VERSION = "2.0.0";
    private static final String TASK_DOCUMENT = "End-to-end task document";

    @Autowired
    private ObjectMapper mapper;

    @LocalServerPort
    private int port;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                // Never throw on 4xx/5xx; the test asserts the status code itself.
                .defaultStatusHandler(code -> true, (request, response) -> {
                })
                .build();
    }

    private ResponseEntity<String> postJson(String path, Object body) {
        return client.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapper.writeValueAsString(body))
                .retrieve()
                .toEntity(String.class);
    }

    private JsonNode getJson(String path) {
        ResponseEntity<String> response = client.get().uri(path).retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return mapper.readTree(response.getBody());
    }

    private Map<String, Object> contractBody() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("signature", Map.of("name", "resize"));
        payload.put("invariants", List.of("dims_match", "alpha_preserved"));
        payload.put("resourceBudget", Map.of("timeMs", 12, "peakMemMb", 8, "artifactKb", 64));
        payload.put("oracle", Map.of("holdout", "secret", "properties", "ref"));
        payload.put("scoring", Map.of("gate", "all_pass"));
        payload.put("samples", List.of(1, 2));
        payload.put("realExamples", List.of(3));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("moduleId", MODULE);
        body.put("version", VERSION);
        body.put("taskKind", "optimize");
        body.put("language", "java");
        body.put("disclosureTier", "L2");
        body.put("taskDocument", TASK_DOCUMENT);
        body.put("payload", payload);
        return body;
    }

    @Test
    void fullJourneyFromPublishToSignedAcceptance() throws Exception {
        // 1. The company publishes the contract.
        ResponseEntity<String> created = postJson("/contracts", contractBody());
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode contract = mapper.readTree(created.getBody());
        String id = contract.get("id").asText();
        assertThat(contract.get("lifecycleState").asText()).isEqualTo("draft");

        // 2. A worker browses the registry.
        JsonNode listed = getJson("/contracts?moduleId=" + MODULE);
        assertThat(listed.get("totalElements").asLong()).isGreaterThanOrEqualTo(1);

        // 3. The public workbench is tier-filtered: secrets never travel.
        JsonNode workbench = getJson("/contracts/" + MODULE + "/" + VERSION + "/workbench");
        assertThat(workbench.get("payload").has("holdout")).isFalse();
        assertThat(workbench.get("payload").get("oracle").has("holdout")).isFalse();
        assertThat(workbench.get("payload").has("samples")).isTrue();

        // 4. A wrong signature is refused (fail closed).
        Map<String, Object> badAccept = new LinkedHashMap<>();
        badAccept.put("workerId", "worker-e2e");
        badAccept.put("agreements", List.of("honesty", "scope", "ai-declaration",
                "confidentiality", "responsibility", "license"));
        badAccept.put("publicKey", Base64.getEncoder().encodeToString(
                KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic().getEncoded()));
        badAccept.put("signature", Base64.getEncoder().encodeToString(new byte[64]));
        assertThat(postJson("/contracts/" + id + "/accept", badAccept).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // 5. The worker signs and accepts.
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String hash = MessageSigner.sha256(TASK_DOCUMENT);
        String message = MessageSigner.canonicalAcceptMessage(MODULE, VERSION, hash);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(message.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> accept = new LinkedHashMap<>();
        accept.put("workerId", "worker-e2e");
        accept.put("agreements", List.of("honesty", "scope", "ai-declaration",
                "confidentiality", "responsibility", "license"));
        accept.put("publicKey", Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        accept.put("signature", Base64.getEncoder().encodeToString(signer.sign()));

        ResponseEntity<String> accepted = postJson("/contracts/" + id + "/accept", accept);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode acceptance = mapper.readTree(accepted.getBody());
        assertThat(acceptance.get("verified").asBoolean()).isTrue();
        assertThat(acceptance.get("taskDocumentHash").asText()).isEqualTo(hash);

        // 6. The lifecycle advanced to accepted.
        JsonNode after = getJson("/contracts/" + MODULE + "/" + VERSION);
        assertThat(after.get("lifecycleState").asText()).isEqualTo("accepted");
    }

    @Test
    void missingAgreementIsRefused() throws Exception {
        String moduleId = MODULE + ".no-agreements";
        Map<String, Object> body = contractBody();
        body.put("moduleId", moduleId);
        body.put("version", "1.0.0");
        String id = mapper.readTree(postJson("/contracts", body).getBody()).get("id").asText();

        Map<String, Object> accept = new LinkedHashMap<>();
        accept.put("workerId", "worker-e2e");
        accept.put("agreements", List.of("honesty"));  // missing five
        accept.put("publicKey", Base64.getEncoder().encodeToString(
                KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic().getEncoded()));
        accept.put("signature", Base64.getEncoder().encodeToString(new byte[64]));

        assertThat(postJson("/contracts/" + id + "/accept", accept).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
