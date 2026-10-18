package io.symmetry.build;

/**
 * The result of compiling a submission with a pinned toolchain.
 *
 * @param artifactFingerprint hash of the produced artifact (bytecode/wasm/binary)
 * @param toolVersion         the exact compiler version used
 * @param reproducible        whether a second build produced the same fingerprint
 */
public record BuildResult(String artifactFingerprint, String toolVersion, boolean reproducible) {
}
