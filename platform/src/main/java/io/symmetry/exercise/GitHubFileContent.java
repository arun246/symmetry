package io.symmetry.exercise;

public record GitHubFileContent(String path, String sha, long size, String content) {
}
