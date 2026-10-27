package io.symmetry.exercise;

import jakarta.validation.constraints.NotBlank;

public record StaticCheckRequest(@NotBlank String content) {
}
