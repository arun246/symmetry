package io.symmetry.runner;

import java.time.Instant;
import java.util.UUID;

public record HeartbeatView(
        UUID id,
        String status,
        Instant lastSeenAt,
        Instant serverTime,
        long leaseSeconds) {
}
