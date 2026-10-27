package io.symmetry.exercise;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "exercise_side_case")
public class ExerciseSideCase {

    @Id
    private UUID id;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "test_id")
    private UUID testId;

    @Column(nullable = false, columnDefinition = "text")
    private String name;

    @Column(nullable = false, columnDefinition = "text")
    private String visibility = "PUBLIC";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_json", nullable = false, columnDefinition = "jsonb")
    private String inputJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "expected_json", nullable = false, columnDefinition = "jsonb")
    private String expectedJson;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt = Instant.now();

    protected ExerciseSideCase() {
    }

    public ExerciseSideCase(UUID id, UUID exerciseId, UUID testId, String name, String visibility,
            String inputJson, String expectedJson) {
        this.id = id;
        this.exerciseId = exerciseId;
        this.testId = testId;
        this.name = name;
        this.visibility = visibility;
        this.inputJson = inputJson;
        this.expectedJson = expectedJson;
    }

    public UUID getId() {
        return id;
    }

    public UUID getExerciseId() {
        return exerciseId;
    }

    public UUID getTestId() {
        return testId;
    }

    public String getName() {
        return name;
    }

    public String getVisibility() {
        return visibility;
    }

    public String getInputJson() {
        return inputJson;
    }

    public String getExpectedJson() {
        return expectedJson;
    }

    public boolean isPublic() {
        return "PUBLIC".equals(visibility);
    }
}
