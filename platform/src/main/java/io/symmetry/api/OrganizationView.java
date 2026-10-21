package io.symmetry.api;

import java.time.Instant;
import java.util.UUID;

/** An organization with the totals an admin needs at a glance. */
public record OrganizationView(
        UUID id,
        String slug,
        String name,
        String status,
        long contracts,
        long workers,
        Instant createdAt) {
}
