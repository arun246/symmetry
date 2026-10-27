package io.symmetry.contract;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Fingerprinting and verification for signed acceptance records.
 *
 * <p>The worker signs a canonical message that binds them to a specific contract
 * <em>version</em> and the exact task document they saw, using an Ed25519 key
 * (JDK 21 built-in). The public key is supplied with the acceptance; wiring it to
 * a durable worker identity is the job of the {@code trust/} module.
 */
public final class MessageSigner {

    private static final String ALGORITHM = "Ed25519";

    private MessageSigner() {
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** The exact text a worker signs when accepting a task document. */
    public static String canonicalAcceptMessage(String moduleId, String version, String taskDocumentHash) {
        return "accept:" + moduleId + "@" + version + ":" + taskDocumentHash;
    }

    /** The exact text a worker signs when accepting an exercise. */
    public static String canonicalExerciseAcceptMessage(UUID exerciseId, int version, String taskDocumentHash) {
        return "accept:exercise:" + exerciseId + ":" + version + ":" + taskDocumentHash;
    }

    /**
     * The exact text a runner signs for a result. The payload hash is over the
     * exact JSON text the runner sent, so no canonical-JSON implementation is
     * needed on either side.
     */
    public static String canonicalResultMessage(UUID leaseId, String nonce, UUID contractId,
            String payloadHash) {
        return "result:" + leaseId + ":" + nonce + ":" + contractId + ":" + payloadHash;
    }

    /** True if the value is a usable Ed25519 public key (X.509, base64). */
    public static boolean validPublicKey(String publicKeyBase64) {
        if (publicKeyBase64 == null || publicKeyBase64.isBlank()) {
            return false;
        }
        try {
            KeyFactory.getInstance(ALGORITHM)
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)));
            return true;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Verify an Ed25519 signature.
     *
     * @param publicKeyBase64 X.509 ({@code SubjectPublicKeyInfo}) encoded public key
     * @param signatureBase64 raw Ed25519 signature bytes
     * @param message         the canonical message that was signed
     * @return true only if the signature is valid; false on any malformed input
     */
    public static boolean verify(String publicKeyBase64, String signatureBase64, String message) {
        if (publicKeyBase64 == null || publicKeyBase64.isBlank()
                || signatureBase64 == null || signatureBase64.isBlank()) {
            return false;
        }
        try {
            PublicKey key = KeyFactory.getInstance(ALGORITHM)
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)));
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(key);
            verifier.update(message.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }
}
