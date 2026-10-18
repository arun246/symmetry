package io.symmetry.contract;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * Incoming payload for a worker accepting a task document.
 *
 * <p>The worker signs the canonical acceptance message with an Ed25519 key; the
 * public key is supplied here (X.509/base64) until a durable identity store owns it.
 * {@code agreements} must include every agreement required by the platform.
 */
public record AcceptRequest(
        @NotBlank String workerId,
        @NotEmpty Set<String> agreements,
        @NotBlank String publicKey,
        @NotBlank String signature) {
}
