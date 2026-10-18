package io.symmetry.eval;

/**
 * The capabilities a module is allowed to use while running (socket 5 in
 * {@code docs/FUTURE-PROOFING.md}).
 *
 * <p>V1 hands every module {@link #EMPTY}: no network, no filesystem, no clock, no
 * randomness, a single thread. Future workloads (services, OS work) get a "seeded"
 * keycard instead of a special-case code path.
 */
public record Keycard(
        boolean network,
        boolean filesystem,
        boolean clock,
        boolean randomness,
        int maxThreads) {

    public static final Keycard EMPTY = new Keycard(false, false, false, false, 1);

    public Keycard {
        if (maxThreads < 1) {
            throw new IllegalArgumentException("maxThreads must be >= 1");
        }
    }
}
