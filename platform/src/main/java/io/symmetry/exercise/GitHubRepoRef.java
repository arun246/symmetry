package io.symmetry.exercise;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record GitHubRepoRef(String owner, String name) {

    private static final Pattern OWNER = Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$");
    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9._-]{1,100}$");

    public static GitHubRepoRef parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Repository URL is required");
        }
        String raw = value.trim();
        if (raw.startsWith("git@github.com:")) {
            raw = raw.substring("git@github.com:".length());
        } else if (raw.startsWith("https://github.com/")) {
            raw = raw.substring("https://github.com/".length());
        } else if (raw.startsWith("http://github.com/")) {
            raw = raw.substring("http://github.com/".length());
        } else if (raw.startsWith("github.com/")) {
            raw = raw.substring("github.com/".length());
        } else {
            throw new IllegalArgumentException("Only public GitHub repositories are supported");
        }
        int tree = raw.indexOf("/tree/");
        if (tree >= 0) {
            raw = raw.substring(0, tree);
        }
        if (raw.endsWith(".git")) {
            raw = raw.substring(0, raw.length() - 4);
        }
        String[] parts = raw.split("/");
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("Repository URL must look like https://github.com/owner/repo");
        }
        String owner = parts[0];
        String name = parts[1];
        if (!OWNER.matcher(owner).matches() || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Repository owner or name contains invalid characters");
        }
        return new GitHubRepoRef(owner, name);
    }

    public String canonicalUrl() {
        return "https://github.com/" + owner + "/" + name;
    }

    public String extension() {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
