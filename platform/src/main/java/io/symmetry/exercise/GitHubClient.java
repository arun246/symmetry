package io.symmetry.exercise;

import java.util.List;

public interface GitHubClient {

    GitHubRepo repo(String owner, String name);

    String resolveCommit(String owner, String name, String ref);

    List<GitHubTreeEntry> tree(String owner, String name, String commitSha);

    GitHubFileContent file(String owner, String name, String path, String ref);
}
