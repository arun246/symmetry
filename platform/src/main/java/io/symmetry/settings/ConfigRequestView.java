package io.symmetry.settings;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/** HTTP view of a configuration request, with the value parsed back to JSON. */
public record ConfigRequestView(
        UUID id,
        UUID orgId,
        UUID contractId,
        UUID requestedBy,
        String key,
        JsonNode value,
        String reason,
        String status,
        UUID reviewedBy,
        Instant reviewedAt,
        String reviewNote,
        Instant createdAt) {

    public static ConfigRequestView from(ConfigRequest request, JsonNode value) {
        return new ConfigRequestView(request.getId(), request.getOrgId(), request.getContractId(),
                request.getRequestedBy(), request.getKey(), value, request.getReason(),
                request.getStatus().name(), request.getReviewedBy(), request.getReviewedAt(),
                request.getReviewNote(), request.getCreatedAt());
    }
}
