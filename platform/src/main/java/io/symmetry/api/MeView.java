package io.symmetry.api;

import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/** The signed-in caller plus the settings that apply to them right now. */
public record MeView(
        UUID id,
        String email,
        String name,
        String role,
        UUID orgId,
        String orgSlug,
        Map<String, JsonNode> settings) {
}
