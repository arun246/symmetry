package io.symmetry.identity;

import java.util.UUID;

/** The authenticated caller, resolved once per request. */
public record Actor(UUID id, String email, String name, Role role, UUID orgId, String orgSlug) {

    public static Actor system() {
        return new Actor(null, "system", "System", Role.ADMIN, null, null);
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isPublisher() {
        return role == Role.PUBLISHER;
    }

    public boolean isWorker() {
        return role == Role.WORKER;
    }

    /** The stable worker key used by acceptance rows until a worker profile exists. */
    public String workerId() {
        return email;
    }
}
