package io.symmetry.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.symmetry.contract.MessageSigner;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(ExerciseApiIntegrationTest.FakeGitHubConfig.class)
class ExerciseApiIntegrationTest {

    private static final String PUBLISHER = "publisher@symmetry.local";
    private static final String WORKER = "worker@symmetry.local";
    private static final String USER_HEADER = "X-Symmetry-User";

    private static final String SOURCE = """
            package demo;

            public class Interval {

                public static int[] merge(int[] left, int[] right) {
                    return left;
                }

                public int size() {
                    return 1;
                }
            }
            """;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    @TestConfiguration
    static class FakeGitHubConfig {

        @Bean
        @Primary
        GitHubClient fakeGitHubClient() {
            return new FakeGitHubClient();
        }
    }

    static final class FakeGitHubClient implements GitHubClient {

        @Override
        public GitHubRepo repo(String owner, String name) {
            return new GitHubRepo(owner, name, "main", "MIT");
        }

        @Override
        public String resolveCommit(String owner, String name, String ref) {
            return "0123456789abcdef0123456789abcdef01234567";
        }

        @Override
        public List<GitHubTreeEntry> tree(String owner, String name, String commitSha) {
            return List.of(
                    new GitHubTreeEntry("src/Interval.java", "blob-sha",
                            SOURCE.getBytes(StandardCharsets.UTF_8).length),
                    new GitHubTreeEntry("README.md", "readme-sha", 20));
        }

        @Override
        public GitHubFileContent file(String owner, String name, String path, String ref) {
            return new GitHubFileContent(path, "blob-sha",
                    SOURCE.getBytes(StandardCharsets.UTF_8).length, SOURCE);
        }
    }

    @Test
    void publisherCreatesImportsPublishesAndWorkerSubmits() throws Exception {
        JsonNode published = publishAnExercise("Interval merge end-to-end");

        String id = published.get("id").asText();
        String taskDocument = published.get("taskDocument").asText();
        assertThat(taskDocument).contains("merge", "Public tests", "Price tier: $$");
        assertThat(taskDocument).doesNotContain("USD", "Offered price");

        JsonNode workerDetail = json(mockMvc.perform(get("/exercises/{id}", id)
                        .header(USER_HEADER, WORKER))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(workerDetail.get("source").get("content").isNull()).isTrue();
        assertThat(workerDetail.get("tests").size()).isEqualTo(1);
        assertThat(workerDetail.get("tests").get(0).get("visibility").asText()).isEqualTo("PUBLIC");
        assertThat(workerDetail.get("sideCases").size()).isEqualTo(1);

        JsonNode ownerDetail = json(mockMvc.perform(get("/exercises/{id}", id)
                        .header(USER_HEADER, PUBLISHER))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(ownerDetail.get("source").get("content").asText()).contains("return left;");
        assertThat(ownerDetail.get("tests").size()).isEqualTo(2);

        KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String hash = MessageSigner.sha256(taskDocument);
        String message = MessageSigner.canonicalExerciseAcceptMessage(
                UUID.fromString(id), published.get("version").asInt(), hash);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keys.getPrivate());
        signer.update(message.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> accept = new LinkedHashMap<>();
        accept.put("agreements", List.of("rights", "confidentiality", "ai-declaration",
                "responsibility", "license"));
        accept.put("publicKey", Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()));
        accept.put("signature", Base64.getEncoder().encodeToString(signer.sign()));

        mockMvc.perform(post("/exercises/{id}/accept", id)
                        .header(USER_HEADER, WORKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(accept)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.taskDocumentHash").value(hash))
                .andExpect(jsonPath("$.workerId").value(WORKER));

        mockMvc.perform(post("/exercises/{id}/accept", id)
                        .header(USER_HEADER, WORKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(accept)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/exercises/{id}/workspace", id)
                        .header(USER_HEADER, WORKER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionEnabled").value(false))
                .andExpect(jsonPath("$.exercise.tests.length()").value(1));

        String code = """
                package demo;

                public class Interval {
                    public static int[] merge(int[] left, int[] right) {
                        return left;
                    }
                }
                """;

        mockMvc.perform(post("/exercises/{id}/static-check", id)
                        .header(USER_HEADER, WORKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("content", code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true));

        mockMvc.perform(post("/exercises/{id}/submissions", id)
                        .header(USER_HEADER, WORKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("content", code))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.workerId").value(WORKER));

        mockMvc.perform(get("/exercises/{id}/submissions", id)
                        .header(USER_HEADER, PUBLISHER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"));

        mockMvc.perform(get("/exercises/{id}/submissions", id)
                        .header(USER_HEADER, WORKER))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/exercises/accepted")
                        .header(USER_HEADER, WORKER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].exerciseId").value(id));

        mockMvc.perform(get("/exercises")
                        .header(USER_HEADER, WORKER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + id + "')].title")
                        .value("Interval merge end-to-end"));
    }

    @Test
    void draftIsPrivateAndPublishedExerciseIsImmutable() throws Exception {
        String id = createDraft("Not published yet");

        mockMvc.perform(get("/exercises/{id}", id)
                        .header(USER_HEADER, WORKER))
                .andExpect(status().isNotFound());

        JsonNode publicList = json(mockMvc.perform(get("/exercises"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(ids(publicList.get("content"))).doesNotContain(id);

        JsonNode mine = json(mockMvc.perform(get("/exercises/mine")
                        .header(USER_HEADER, PUBLISHER))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(ids(mine.get("content"))).contains(id);

        String publishedId = publishAnExercise("Immutable exercise").get("id").asText();
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("title", "Changed");
        update.put("summary", "Changed");
        update.put("language", "java");
        update.put("complexity", "HARD");
        update.put("priceCents", 100);
        update.put("currency", "USD");
        update.put("deadlineDays", 3);
        update.put("taskDocument", "");

        mockMvc.perform(patch("/exercises/{id}", publishedId)
                        .header(USER_HEADER, PUBLISHER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(update)))
                .andExpect(status().isConflict());
    }

    @Test
    void badSignatureAndWrongRoleAreRejected() throws Exception {
        JsonNode published = publishAnExercise("Rejected acceptance");
        String id = published.get("id").asText();

        Map<String, Object> accept = new LinkedHashMap<>();
        accept.put("agreements", List.of("rights", "confidentiality", "ai-declaration",
                "responsibility", "license"));
        accept.put("publicKey", "not-a-key");
        accept.put("signature", "not-a-signature");

        mockMvc.perform(post("/exercises/{id}/accept", id)
                        .header(USER_HEADER, WORKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(accept)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/exercises/{id}/accept", id)
                        .header(USER_HEADER, PUBLISHER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(accept)))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptedWorkerPublishesSubtaskWithPriceTier() throws Exception {
        JsonNode parent = publishAnExercise("Parent collaboration task");
        String parentId = parent.get("id").asText();
        acceptAsWorker(parentId, parent);

        Map<String, Object> subtaskBody = new LinkedHashMap<>();
        subtaskBody.put("title", "Write the merge helper");
        subtaskBody.put("summary", "Implement the inner merge step for the parent task.");
        subtaskBody.put("complexity", "HARD");
        subtaskBody.put("deadlineDays", 3);
        subtaskBody.put("taskDocument", "");

        JsonNode subtask = json(mockMvc.perform(post("/exercises/{id}/subtasks", parentId)
                        .header(USER_HEADER, WORKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(subtaskBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.parentExerciseId").value(parentId))
                .andExpect(jsonPath("$.createdBy").isNotEmpty())
                .andReturn());
        String subtaskId = subtask.get("id").asText();

        mockMvc.perform(post("/exercises/{id}/subtasks", parentId)
                        .header(USER_HEADER, PUBLISHER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(subtaskBody)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/exercises/{id}/source", subtaskId)
                        .header(USER_HEADER, WORKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "repoUrl", "https://github.com/acme/code",
                                "filePath", "src/Interval.java"))))
                .andExpect(status().isOk());

        JsonNode symbols = json(mockMvc.perform(get("/exercises/{id}/symbols", subtaskId)
                        .header(USER_HEADER, WORKER))
                .andExpect(status().isOk())
                .andReturn());
        int mergeLine = 0;
        for (JsonNode symbol : symbols) {
            if ("merge".equals(symbol.get("name").asText())) {
                mergeLine = symbol.get("startLine").asInt();
            }
        }

        JsonNode stub = json(mockMvc.perform(post("/exercises/{id}/stubs", subtaskId)
                        .header(USER_HEADER, WORKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "symbolName", "merge", "startLine", mergeLine))))
                .andExpect(status().isCreated())
                .andReturn());

        Map<String, Object> tests = new LinkedHashMap<>();
        tests.put("tests", List.of(Map.of(
                "stubId", stub.get("id").asText(), "name", "subtask_public",
                "visibility", "PUBLIC", "content", "assert merge([], []) == []", "timeoutMs", 2000)));
        tests.put("sideCases", List.of());

        mockMvc.perform(put("/exercises/{id}/tests", subtaskId)
                        .header(USER_HEADER, WORKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(tests)))
                .andExpect(status().isOk());

        JsonNode published = json(mockMvc.perform(post("/exercises/{id}/publish", subtaskId)
                        .header(USER_HEADER, WORKER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.parentExerciseId").value(parentId))
                .andReturn());

        assertThat(published.get("taskDocument").asText())
                .contains("Price tier: $$$", "Parent task: " + parentId)
                .doesNotContain("USD");

        mockMvc.perform(get("/exercises")
                        .header(USER_HEADER, WORKER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + subtaskId + "')].parentExerciseId")
                        .value(parentId));

        Map<String, Object> ownAccept = new LinkedHashMap<>();
        ownAccept.put("agreements", List.of("rights", "confidentiality", "ai-declaration",
                "responsibility", "license"));
        ownAccept.put("publicKey", "not-a-key");
        ownAccept.put("signature", "not-a-signature");
        mockMvc.perform(post("/exercises/{id}/accept", subtaskId)
                        .header(USER_HEADER, WORKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(ownAccept)))
                .andExpect(status().isBadRequest());
    }

    private void acceptAsWorker(String id, JsonNode published) throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String hash = MessageSigner.sha256(published.get("taskDocument").asText());
        String message = MessageSigner.canonicalExerciseAcceptMessage(
                UUID.fromString(id), published.get("version").asInt(), hash);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keys.getPrivate());
        signer.update(message.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> accept = new LinkedHashMap<>();
        accept.put("agreements", List.of("rights", "confidentiality", "ai-declaration",
                "responsibility", "license"));
        accept.put("publicKey", Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()));
        accept.put("signature", Base64.getEncoder().encodeToString(signer.sign()));

        mockMvc.perform(post("/exercises/{id}/accept", id)
                        .header(USER_HEADER, WORKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(accept)))
                .andExpect(status().isCreated());
    }

    private JsonNode publishAnExercise(String title) throws Exception {
        String id = createDraft(title);

        mockMvc.perform(post("/exercises/{id}/source", id)
                        .header(USER_HEADER, PUBLISHER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "repoUrl", "https://github.com/acme/code",
                                "filePath", "src/Interval.java"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.license").value("MIT"))
                .andExpect(jsonPath("$.content").exists());

        JsonNode symbols = json(mockMvc.perform(get("/exercises/{id}/symbols", id)
                        .header(USER_HEADER, PUBLISHER))
                .andExpect(status().isOk())
                .andReturn());
        int mergeLine = 0;
        for (JsonNode symbol : symbols) {
            if ("merge".equals(symbol.get("name").asText())) {
                mergeLine = symbol.get("startLine").asInt();
            }
        }
        assertThat(mergeLine).isEqualTo(5);

        JsonNode stub = json(mockMvc.perform(post("/exercises/{id}/stubs", id)
                        .header(USER_HEADER, PUBLISHER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "symbolName", "merge",
                                "startLine", mergeLine))))
                .andExpect(status().isCreated())
                .andReturn());

        Map<String, Object> tests = new LinkedHashMap<>();
        tests.put("tests", List.of(
                Map.of("stubId", stub.get("id").asText(), "name", "merge_public",
                        "visibility", "PUBLIC", "content", "assert merge([], []) == []",
                        "timeoutMs", 2000),
                Map.of("stubId", stub.get("id").asText(), "name", "merge_hidden",
                        "visibility", "HIDDEN", "content", "assert merge([1], [2]) == [1, 2]",
                        "timeoutMs", 2000)));
        tests.put("sideCases", List.of(Map.of("name", "empty", "visibility", "PUBLIC",
                "input", List.of(), "expected", List.of())));

        mockMvc.perform(put("/exercises/{id}/tests", id)
                        .header(USER_HEADER, PUBLISHER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(tests)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        return json(mockMvc.perform(post("/exercises/{id}/publish", id)
                        .header(USER_HEADER, PUBLISHER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andReturn());
    }

    private String createDraft(String title) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("summary", "Merge two sorted arrays without mutating the inputs.");
        body.put("language", "java");
        body.put("complexity", "MEDIUM");
        body.put("priceCents", 1500);
        body.put("currency", "USD");
        body.put("deadlineDays", 7);
        body.put("taskDocument", "");

        return json(mockMvc.perform(post("/exercises")
                        .header(USER_HEADER, PUBLISHER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn()).get("id").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private static List<String> ids(JsonNode content) {
        List<String> ids = new java.util.ArrayList<>();
        for (JsonNode node : content) {
            ids.add(node.get("id").asText());
        }
        return ids;
    }
}
