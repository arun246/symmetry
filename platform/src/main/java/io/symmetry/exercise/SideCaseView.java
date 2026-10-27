package io.symmetry.exercise;

import java.util.UUID;

import tools.jackson.databind.JsonNode;

public record SideCaseView(
        UUID id,
        UUID testId,
        String name,
        String visibility,
        JsonNode input,
        JsonNode expected) {
}
