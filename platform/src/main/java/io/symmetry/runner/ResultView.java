package io.symmetry.runner;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

public record ResultView(
        UUID id,
        UUID leaseId,
        UUID contractId,
        boolean passed,
        JsonNode payload,
        String payloadHash,
        boolean quarantined,
        Instant receivedAt) {
}
