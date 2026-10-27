package io.symmetry.exercise;

import jakarta.validation.constraints.NotBlank;

public record SubmissionRequest(@NotBlank String content) {
}
