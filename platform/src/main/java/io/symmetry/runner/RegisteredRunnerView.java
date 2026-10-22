package io.symmetry.runner;

import java.time.Instant;
import java.util.UUID;

/**
 * Registration response. The raw {@code accessToken} is shown once and is not
 * recoverable later; the runner must store it securely.
 */
public record RegisteredRunnerView(
        UUID id,
        String accessToken,
        String status,
        Instant registeredAt,
        long leaseSeconds) {
}
