package io.symmetry.exercise;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.symmetry.contract.MessageSigner;

@Service
public class GitHubImportService {

    private static final long MAX_FILE_BYTES = 512_000;
    private static final List<String> SUPPORTED_EXTENSIONS = List.of("java", "py", "rs");
    private static final List<SecretPattern> SECRET_PATTERNS = List.of(
            new SecretPattern("private key block",
                    Pattern.compile("-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----")),
            new SecretPattern("AWS access key", Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b")),
            new SecretPattern("GitHub token", Pattern.compile("\\bgh[pousr]_[A-Za-z0-9]{30,}\\b")),
            new SecretPattern("Slack token", Pattern.compile("\\bxox[baprs]-[A-Za-z0-9-]{10,}\\b")),
            new SecretPattern("hard-coded password",
                    Pattern.compile("(?i)\\bpassword\\s*=\\s*[\"'][^\"']{4,}[\"']")),
            new SecretPattern("hard-coded secret",
                    Pattern.compile("(?i)\\bsecret\\s*=\\s*[\"'][^\"']{8,}[\"']")));

    private final GitHubClient github;
    private final ExerciseSourceRepository sources;

    public GitHubImportService(GitHubClient github, ExerciseSourceRepository sources) {
        this.github = github;
        this.sources = sources;
    }

    public RepoTreeView preview(String repoUrl) {
        GitHubRepoRef ref = GitHubRepoRef.parse(repoUrl);
        GitHubRepo repo = github.repo(ref.owner(), ref.name());
        String commit = github.resolveCommit(ref.owner(), ref.name(), repo.defaultBranch());
        List<TreeEntryView> files = github.tree(ref.owner(), ref.name(), commit).stream()
                .filter(entry -> supported(entry.path()))
                .filter(entry -> entry.size() <= MAX_FILE_BYTES)
                .map(entry -> new TreeEntryView(entry.path(), entry.sha(), entry.size()))
                .toList();
        return new RepoTreeView(ref.canonicalUrl(), ref.owner(), ref.name(), commit,
                repo.license(), files);
    }

    @Transactional
    public ImportedSource importFile(Exercise exercise, ImportSourceRequest request) {
        GitHubRepoRef ref = GitHubRepoRef.parse(request.repoUrl());
        String path = normalizePath(request.filePath());
        String language = languageOf(path);
        if (!language.equals(exercise.getLanguage().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("File extension does not match the exercise language");
        }
        GitHubRepo repo = github.repo(ref.owner(), ref.name());
        String commit = github.resolveCommit(ref.owner(), ref.name(), repo.defaultBranch());
        GitHubFileContent file = github.file(ref.owner(), ref.name(), path, commit);
        if (file.size() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("File is larger than " + MAX_FILE_BYTES + " bytes");
        }
        if (file.content() == null || file.content().isBlank()) {
            throw new IllegalArgumentException("File is empty");
        }
        List<String> findings = scan(file.content());
        if (repo.license() == null) {
            findings = new ArrayList<>(findings);
            findings.add("No license detected in the repository; the publisher must confirm rights.");
        }
        sources.deleteByExerciseId(exercise.getId());
        ExerciseSource source = sources.save(new ExerciseSource(
                UUID.randomUUID(), exercise.getId(), ref.canonicalUrl(), ref.owner(), ref.name(),
                commit, path, file.sha(), repo.license(), language, file.content(),
                MessageSigner.sha256(file.content())));
        return new ImportedSource(source, List.copyOf(findings));
    }

    public List<String> scanFindings(String content) {
        return scan(content);
    }

    public SourceView view(ExerciseSource source, boolean includeContent, List<String> findings) {
        return new SourceView(source.getRepoUrl(), source.getRepoOwner(), source.getRepoName(),
                source.getCommitSha(), source.getFilePath(), source.getBlobSha(), source.getLicense(),
                source.getLanguage(), source.getContentHash(),
                includeContent ? source.getOriginalContent() : null,
                findings == null ? List.of() : findings);
    }

    private static boolean supported(String path) {
        return SUPPORTED_EXTENSIONS.stream().anyMatch(extension -> path.endsWith("." + extension));
    }

    private static String languageOf(String path) {
        if (path.endsWith(".java")) {
            return "java";
        }
        if (path.endsWith(".py")) {
            return "python";
        }
        if (path.endsWith(".rs")) {
            return "rust";
        }
        throw new IllegalArgumentException("Only Java, Python and Rust source files are supported");
    }

    private static String normalizePath(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("File path is required");
        }
        String path = raw.trim().replace('\\', '/');
        if (path.startsWith("/") || path.contains("..") || path.isBlank()) {
            throw new IllegalArgumentException("File path must be relative and must not contain '..'");
        }
        return path;
    }

    private static List<String> scan(String content) {
        List<String> findings = new ArrayList<>();
        for (SecretPattern pattern : SECRET_PATTERNS) {
            Matcher matcher = pattern.pattern().matcher(content);
            if (matcher.find()) {
                findings.add("Possible " + pattern.label() + " found near line " + lineOf(content, matcher.start()));
            }
        }
        return findings;
    }

    private static int lineOf(String content, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private record SecretPattern(String label, Pattern pattern) {
    }
}
