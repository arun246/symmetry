package io.symmetry.exercise;

import java.util.List;

public record RepoTreeView(
        String repoUrl,
        String repoOwner,
        String repoName,
        String commitSha,
        String license,
        List<TreeEntryView> files) {
}
