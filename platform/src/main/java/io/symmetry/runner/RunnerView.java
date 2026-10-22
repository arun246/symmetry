package io.symmetry.runner;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/** Admin-facing runner view; never contains the access token or its hash. */
public record RunnerView(
        UUID id,
        String name,
        String hostname,
        UUID orgId,
        String publicKey,
        String version,
        JsonNode capabilities,
        String status,
        Instant lastSeenAt,
        Instant registeredAt,
        Instant revokedAt) {
}
