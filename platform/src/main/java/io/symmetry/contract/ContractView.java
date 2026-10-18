package io.symmetry.contract;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/** Public view of a contract. */
public record ContractView(
        UUID id,
        String moduleId,
        String version,
        String taskKind,
        String language,
        String workKind,
        String lifecycleState,
        String disclosureTier,
        String taskDocument,
        JsonNode payload,
        Instant createdAt) {
}
