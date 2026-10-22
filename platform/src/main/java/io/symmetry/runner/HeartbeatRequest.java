package io.symmetry.runner;

import tools.jackson.databind.JsonNode;

/** Optional health update; {@code null} fields are left unchanged. */
public record HeartbeatRequest(String version, JsonNode capabilities) {
}
