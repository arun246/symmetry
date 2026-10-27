package io.symmetry.exercise;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

public record SubmissionView(
        UUID id,
        UUID exerciseId,
        String workerId,
        String status,
        String contentHash,
        JsonNode staticFindings,
        Instant submittedAt) {
}
