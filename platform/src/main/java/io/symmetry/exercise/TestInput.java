package io.symmetry.exercise;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record TestInput(
        UUID stubId,
        @NotBlank String name,
        @NotBlank String visibility,
        @NotBlank String content,
        @Min(100) @Max(60000) int timeoutMs) {
}
