package io.symmetry.contract;

import tools.jackson.databind.JsonNode;

/**
 * The public, tier-filtered view of a contract that a worker may fetch from the
 * workbench ({@code docs/IMPLEMENTATION.md} section 2). The payload has already
 * had anything the disclosure tier forbids removed.
 */
public record WorkbenchView(
        java.util.UUID id,
        String moduleId,
        String version,
        String language,
        String taskKind,
        String workKind,
        String disclosureTier,
        String taskDocument,
        JsonNode payload) {
}
