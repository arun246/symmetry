package io.symmetry.runner;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import io.symmetry.contract.MessageSigner;

/**
 * Machine authentication for runner endpoints.
 *
 * <p>Registration uses a shared secret configured by the operator. Every later
 * request uses the bearer token issued once at registration; only its SHA-256
 * hash is stored. Result payloads are additionally signed with the runner's
 * Ed25519 key, so a stolen bearer token alone cannot forge a verdict.
 */
@Service
public class RunnerAuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final RunnerRepository runners;
    private final String registrationTokenHash;

    public RunnerAuthService(RunnerRepository runners,
            @Value("${symmetry.runner.registration-token:}") String registrationToken) {
        this.runners = runners;
        this.registrationTokenHash = registrationToken == null || registrationToken.isBlank()
                ? null
                : MessageSigner.sha256(registrationToken);
    }

    public void requireRegistrationToken(String candidate) {
        if (registrationTokenHash == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Runner registration is disabled: set symmetry.runner.registration-token");
        }
        if (!constantTimeEquals(MessageSigner.sha256(candidate), registrationTokenHash)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid registration token");
        }
    }

    public Runner authenticate(UUID runnerId, String authorization) {
        String token = bearer(authorization);
        Runner runner = runners.findById(runnerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Runner " + runnerId + " not found"));
        if (!runner.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Runner is revoked");
        }
        if (!constantTimeEquals(MessageSigner.sha256(token), runner.getAccessTokenHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid runner token");
        }
        return runner;
    }

    public static IssuedToken issueToken() {
        String raw = random(32);
        return new IssuedToken(raw, MessageSigner.sha256(raw));
    }

    public static String newNonce() {
        return random(18);
    }

    private static String bearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing runner token");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing runner token");
        }
        return token;
    }

    private static String random(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return ENCODER.encodeToString(value);
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }

    public record IssuedToken(String raw, String hash) {
    }
}
