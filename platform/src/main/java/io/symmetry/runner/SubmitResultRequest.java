package io.symmetry.runner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * A runner's signed verdict. {@code payload} is the exact JSON text that was
 * hashed and signed; it must contain {@code passed}, and may contain
 * {@code failedChecker}, {@code sanitizedMessage}, {@code metrics},
 * {@code artifactDigest} and {@code logsDigest}.
 */
public record SubmitResultRequest(
        @NotNull UUID leaseId,
        @NotBlank String nonce,
        @NotBlank String payload,
        @NotBlank String signature) {
}
