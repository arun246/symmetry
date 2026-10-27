package io.symmetry.exercise;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateSubtaskRequest(
        @NotBlank String title,
        @NotBlank String summary,
        @NotBlank String complexity,
        @Min(1) @Max(30) int deadlineDays,
        String taskDocument) {
}
