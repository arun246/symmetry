package io.symmetry.exercise;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record AcceptExerciseRequest(
        @NotEmpty Set<String> agreements,
        @NotBlank String publicKey,
        @NotBlank String signature) {
}
