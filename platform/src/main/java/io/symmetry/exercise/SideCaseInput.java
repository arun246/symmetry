package io.symmetry.exercise;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.JsonNode;

public record SideCaseInput(
        UUID testId,
        @NotBlank String name,
        @NotBlank String visibility,
        JsonNode input,
        JsonNode expected) {
}
