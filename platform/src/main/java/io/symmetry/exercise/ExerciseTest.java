package io.symmetry.exercise;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "exercise_test")
public class ExerciseTest {

    @Id
    private UUID id;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "stub_id")
    private UUID stubId;

    @Column(nullable = false, columnDefinition = "text")
    private String name;

    @Column(nullable = false, columnDefinition = "text")
    private String visibility = "PUBLIC";

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs = 5000;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt = Instant.now();

    protected ExerciseTest() {
    }

    public ExerciseTest(UUID id, UUID exerciseId, UUID stubId, String name, String visibility,
            String content, int timeoutMs) {
        this.id = id;
        this.exerciseId = exerciseId;
        this.stubId = stubId;
        this.name = name;
        this.visibility = visibility;
        this.content = content;
        this.timeoutMs = timeoutMs;
    }

    public UUID getId() {
        return id;
    }

    public UUID getExerciseId() {
        return exerciseId;
    }

    public UUID getStubId() {
        return stubId;
    }

    public String getName() {
        return name;
    }

    public String getVisibility() {
        return visibility;
    }

    public String getContent() {
        return content;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public boolean isPublic() {
        return "PUBLIC".equals(visibility);
    }
}
