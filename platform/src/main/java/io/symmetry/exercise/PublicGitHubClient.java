package io.symmetry.exercise;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class PublicGitHubClient implements GitHubClient {

    private final RestClient client;
    private final ObjectMapper mapper;

    public PublicGitHubClient(ObjectMapper mapper) {
        this.mapper = mapper;
        this.client = RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader(HttpHeaders.USER_AGENT, "symmetry-platform")
                .build();
    }

    @Override
    public GitHubRepo repo(String owner, String name) {
        JsonNode body = get("/repos/{owner}/{repo}", owner, name);
        String license = body.path("license").path("spdx_id").asString("").trim();
        if (license.isBlank() || "NOASSERTION".equals(license)) {
            license = null;
        }
        return new GitHubRepo(owner, name, body.path("default_branch").asString("main"), license);
    }

    @Override
    public String resolveCommit(String owner, String name, String ref) {
        JsonNode body = get("/repos/{owner}/{repo}/commits/{ref}", owner, name, ref);
        String sha = body.path("sha").asString("").trim();
        if (sha.isBlank()) {
            throw new IllegalArgumentException("GitHub did not return a commit SHA");
        }
        return sha;
    }

    @Override
    public List<GitHubTreeEntry> tree(String owner, String name, String commitSha) {
        JsonNode body = get("/repos/{owner}/{repo}/git/trees/{sha}?recursive=1",
                owner, name, commitSha);
        List<GitHubTreeEntry> files = new ArrayList<>();
        JsonNode tree = body.path("tree");
        if (!tree.isArray()) {
            return files;
        }
        for (JsonNode entry : tree) {
            if (!"blob".equals(entry.path("type").asString(""))) {
                continue;
            }
            files.add(new GitHubTreeEntry(
                    entry.path("path").asString(""),
                    entry.path("sha").asString(""),
                    entry.path("size").asLong(0)));
        }
        return files;
    }

    @Override
    public GitHubFileContent file(String owner, String name, String path, String ref) {
        JsonNode body = get("/repos/{owner}/{repo}/contents/{path}?ref={ref}",
                owner, name, path, ref);
        String encoding = body.path("encoding").asString("");
        String encoded = body.path("content").asString("");
        if (!"base64".equals(encoding) || encoded.isBlank()) {
            throw new IllegalArgumentException("GitHub did not return a base64 text file");
        }
        String content = new String(Base64.getMimeDecoder().decode(encoded), StandardCharsets.UTF_8);
        return new GitHubFileContent(
                body.path("path").asString(path),
                body.path("sha").asString(""),
                body.path("size").asLong(content.length()),
                content);
    }

    private JsonNode get(String template, Object... variables) {
        try {
            String body = client.get()
                    .uri(template, variables)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            return mapper.readTree(body == null ? "{}" : body);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new IllegalArgumentException("Repository, file or commit was not found");
            }
            if (e.getStatusCode().value() == 403) {
                throw new IllegalArgumentException("GitHub rate limit reached; try again later");
            }
            throw new IllegalArgumentException("GitHub request failed with status "
                    + e.getStatusCode().value());
        } catch (RuntimeException e) {
            if (e instanceof IllegalArgumentException) {
                throw e;
            }
            throw new IllegalArgumentException("GitHub request failed: " + e.getMessage());
        }
    }
}
