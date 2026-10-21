package io.symmetry.api;

import java.time.Instant;
import java.util.UUID;

/** An account as an admin sees it. Credentials never leave Keycloak. */
public record UserView(
        UUID id,
        String email,
        String displayName,
        String role,
        UUID orgId,
        String status,
        Instant createdAt) {
}
