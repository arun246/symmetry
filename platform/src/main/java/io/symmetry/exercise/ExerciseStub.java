package io.symmetry.exercise;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "exercise_stub")
public class ExerciseStub {

    @Id
    private UUID id;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "symbol_name", nullable = false, columnDefinition = "text")
    private String symbolName;

    @Column(name = "symbol_kind", nullable = false, columnDefinition = "text")
    private String symbolKind;

    @Column(name = "start_line", nullable = false)
    private int startLine;

    @Column(name = "end_line", nullable = false)
    private int endLine;

    @Column(name = "stub_content", nullable = false, columnDefinition = "text")
    private String stubContent;

    @Column(name = "original_hash", nullable = false, columnDefinition = "text")
    private String originalHash;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt = Instant.now();

    protected ExerciseStub() {
    }

    public ExerciseStub(UUID id, UUID exerciseId, String symbolName, String symbolKind,
            int startLine, int endLine, String stubContent, String originalHash) {
        this.id = id;
        this.exerciseId = exerciseId;
        this.symbolName = symbolName;
        this.symbolKind = symbolKind;
        this.startLine = startLine;
        this.endLine = endLine;
        this.stubContent = stubContent;
        this.originalHash = originalHash;
    }

    public UUID getId() {
        return id;
    }

    public UUID getExerciseId() {
        return exerciseId;
    }

    public String getSymbolName() {
        return symbolName;
    }

    public String getSymbolKind() {
        return symbolKind;
    }

    public int getStartLine() {
        return startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    public String getStubContent() {
        return stubContent;
    }

    public String getOriginalHash() {
        return originalHash;
    }
}
