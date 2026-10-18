package io.symmetry.contract;

import tools.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;

/**
 * Incoming payload for publishing a contract. {@code payload} carries the full
 * JSON document (signature, invariants, budgets, oracle, scoring). {@code workKind}
 * is optional and defaults to {@code compute}.
 */
public record ContractRequest(
        @NotBlank String moduleId,
        @NotBlank String version,
        @NotBlank String taskKind,
        @NotBlank String language,
        @NotBlank String disclosureTier,
        String workKind,
        @NotBlank String taskDocument,
        JsonNode payload) {
}
