package com.blockworld.net;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.security.SecureRandom;
import static org.junit.jupiter.api.Assertions.*;

class MultiplayerAccountStoreTest {
    @Test void challengeResponseRegistrationAndAuthentication(@TempDir Path directory) throws Exception {
        Path world = directory.resolve("account-world.bwserver");
        MultiplayerAccountStore first = new MultiplayerAccountStore(world);
        byte[] salt = new byte[16];
        byte[] nonce = new byte[32];
        new SecureRandom().nextBytes(salt);
        new SecureRandom().nextBytes(nonce);
        byte[] verifier = MultiplayerAccountStore.deriveVerifier("UniquePass-2026".toCharArray(), salt);
        byte[] proof = MultiplayerAccountStore.proof(verifier, nonce);

        assertTrue(first.register("Builder", salt, verifier, nonce, proof));
        assertTrue(first.exists("builder"));
        assertTrue(first.authenticate("Builder", nonce, proof));
        assertFalse(first.register("Builder", salt, verifier, nonce, proof));

        MultiplayerAccountStore restored = new MultiplayerAccountStore(world);
        restored.load();
        byte[] restoredSalt = restored.saltFor("Builder");
        byte[] secondNonce = new byte[32];
        new SecureRandom().nextBytes(secondNonce);
        byte[] restoredVerifier = MultiplayerAccountStore.deriveVerifier(
                "UniquePass-2026".toCharArray(), restoredSalt);
        assertTrue(restored.authenticate("Builder", secondNonce,
                MultiplayerAccountStore.proof(restoredVerifier, secondNonce)));
        assertFalse(restored.authenticate("Builder", secondNonce, proof));
    }

    @Test void accountPasswordsMustMeetMinimumLength() {
        assertThrows(IllegalArgumentException.class, () ->
                MultiplayerAccountStore.deriveVerifier("short".toCharArray(), new byte[16]));
    }
}
