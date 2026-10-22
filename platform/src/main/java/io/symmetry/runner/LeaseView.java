package io.symmetry.runner;

import java.time.Instant;
import java.util.UUID;

/**
 * A leased job. The runner gets the contract identity and task-document
 * fingerprint, not the hidden payload; submission/source binding arrives with
 * the submission module.
 */
public record LeaseView(
        UUID leaseId,
        UUID contractId,
        String moduleId,
        String version,
        String language,
        String taskDocumentHash,
        String nonce,
        Instant leasedAt,
        Instant expiresAt) {
}
