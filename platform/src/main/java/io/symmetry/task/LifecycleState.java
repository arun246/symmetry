package io.symmetry.task;

/**
 * The states a contract moves through (socket 7 in {@code docs/FUTURE-PROOFING.md}).
 *
 * <p>V1 walks the shortest path ({@code DRAFT → ACCEPTED}); the full sequence is
 * recorded now so back-and-forth negotiation, revisions, and merges can be added
 * without changing the stored shape.
 */
public enum LifecycleState {
    DRAFT,
    NEGOTIATED,
    ACCEPTED,
    SUBMITTED,
    JUDGED,
    MERGED
}
