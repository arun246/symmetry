package io.symmetry.exercise;

import jakarta.validation.constraints.NotBlank;

public record ImportSourceRequest(@NotBlank String repoUrl, @NotBlank String filePath) {
}
