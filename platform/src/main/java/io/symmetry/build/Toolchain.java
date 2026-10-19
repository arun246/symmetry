package io.symmetry.build;

/**
 * A compiler connector.
 *
 * <p>Adding a language means implementing this interface and registering it as a
 * Spring bean; nothing in the center of the system changes. Implementations land
 * in M3 ({@code docs/IMPLEMENTATION.md}); this interface exists now so the registry
 * is plugin-shaped rather than an {@code if (java) ... else if (rust)} block.
 */
public interface Toolchain {

    /** Canonical lowercase language id, e.g. {@code "java"} or {@code "rust"}. */
    String language();

    /** The exact pinned tool version, or {@code ""} if not yet pinned. */
    default String toolVersion() {
        return "";
    }

    /** Whether the pinned compiler/runtime is present in this environment. */
    default boolean available() {
        return false;
    }
}
