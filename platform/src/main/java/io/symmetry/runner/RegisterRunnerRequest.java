package io.symmetry.runner;

import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.JsonNode;

/**
 * Incoming registration for a publisher appliance. The registration token is a
 * shared secret configured on the control plane; the runner supplies its
 * Ed25519 public key, and the response contains the bearer token it must use.
 */
public record RegisterRunnerRequest(
        @NotBlank String registrationToken,
        @NotBlank String name,
        @NotBlank String hostname,
        @NotBlank String version,
        @NotBlank String publicKey,
        JsonNode capabilities) {
}
