package io.symmetry.exercise;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "exercise_source")
public class ExerciseSource {

    @Id
    private UUID id;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "repo_url", nullable = false, columnDefinition = "text")
    private String repoUrl;

    @Column(name = "repo_owner", nullable = false, columnDefinition = "text")
    private String repoOwner;

    @Column(name = "repo_name", nullable = false, columnDefinition = "text")
    private String repoName;

    @Column(name = "commit_sha", nullable = false, columnDefinition = "text")
    private String commitSha;

    @Column(name = "file_path", nullable = false, columnDefinition = "text")
    private String filePath;

    @Column(name = "blob_sha", columnDefinition = "text")
    private String blobSha;

    @Column(columnDefinition = "text")
    private String license;

    @Column(nullable = false, columnDefinition = "text")
    private String language;

    @Column(name = "original_content", nullable = false, columnDefinition = "text")
    private String originalContent;

    @Column(name = "content_hash", nullable = false, columnDefinition = "text")
    private String contentHash;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt = Instant.now();

    protected ExerciseSource() {
    }

    public ExerciseSource(UUID id, UUID exerciseId, String repoUrl, String repoOwner, String repoName,
            String commitSha, String filePath, String blobSha, String license, String language,
            String originalContent, String contentHash) {
        this.id = id;
        this.exerciseId = exerciseId;
        this.repoUrl = repoUrl;
        this.repoOwner = repoOwner;
        this.repoName = repoName;
        this.commitSha = commitSha;
        this.filePath = filePath;
        this.blobSha = blobSha;
        this.license = license;
        this.language = language;
        this.originalContent = originalContent;
        this.contentHash = contentHash;
    }

    public UUID getId() {
        return id;
    }

    public UUID getExerciseId() {
        return exerciseId;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public String getRepoOwner() {
        return repoOwner;
    }

    public String getRepoName() {
        return repoName;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getBlobSha() {
        return blobSha;
    }

    public String getLicense() {
        return license;
    }

    public String getLanguage() {
        return language;
    }

    public String getOriginalContent() {
        return originalContent;
    }

    public String getContentHash() {
        return contentHash;
    }
}
