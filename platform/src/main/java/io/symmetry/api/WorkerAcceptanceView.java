package io.symmetry.api;

import java.time.Instant;
import java.util.UUID;

/** A worker's acceptance joined with the contract it points at. */
public record WorkerAcceptanceView(
        UUID id,
        UUID contractId,
        String moduleId,
        String version,
        String lifecycleState,
        String taskDocumentHash,
        boolean verified,
        Instant acceptedAt,
        String status,
        Instant withdrawnAt) {
}
