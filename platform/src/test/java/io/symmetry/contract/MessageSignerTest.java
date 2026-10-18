package io.symmetry.contract;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class MessageSignerTest {

    private static final String ALGORITHM = "Ed25519";

    @Test
    void verifiesAValidSignature() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
        String hash = MessageSigner.sha256("task document body");
        String message = MessageSigner.canonicalAcceptMessage("module.image.resize", "1.0.0", hash);

        Signature signer = Signature.getInstance(ALGORITHM);
        signer.initSign(keyPair.getPrivate());
        signer.update(message.getBytes(StandardCharsets.UTF_8));

        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        String signature = Base64.getEncoder().encodeToString(signer.sign());

        assertTrue(MessageSigner.verify(publicKey, signature, message));
        assertFalse(MessageSigner.verify(publicKey, signature, message + " tampered"));
        assertFalse(MessageSigner.verify(publicKey, signature, "accept:other@9.9.9:" + hash));
    }

    @Test
    void failsClosedOnMalformedInput() {
        assertFalse(MessageSigner.verify(null, "sig", "message"));
        assertFalse(MessageSigner.verify("not-base64", "not-base64", "message"));
        assertFalse(MessageSigner.verify(" ", " ", "message"));
    }

    @Test
    void hashIsStableAndVersionBindsMessage() {
        assertNotEquals(
                MessageSigner.canonicalAcceptMessage("m", "1.0.0", "h"),
                MessageSigner.canonicalAcceptMessage("m", "2.0.0", "h"));
    }
}
