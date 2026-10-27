package io.symmetry.exercise;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateExerciseRequest(
        @NotBlank String title,
        @NotBlank String summary,
        @NotBlank String language,
        @NotBlank String complexity,
        @PositiveOrZero long priceCents,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @Min(1) @Max(30) int deadlineDays,
        String taskDocument) {
}
