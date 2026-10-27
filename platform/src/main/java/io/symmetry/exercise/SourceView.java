package io.symmetry.exercise;

import java.util.List;

public record SourceView(
        String repoUrl,
        String repoOwner,
        String repoName,
        String commitSha,
        String filePath,
        String blobSha,
        String license,
        String language,
        String contentHash,
        String content,
        List<String> findings) {
}
