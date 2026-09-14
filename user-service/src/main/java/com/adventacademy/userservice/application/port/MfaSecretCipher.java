package com.adventacademy.userservice.application.port;

/**
 * Encrypts TOTP secrets at rest with a versioned key, so the seed cannot be read from the database
 * without the key and keys can be rotated without re-enrolling everybody at once.
 */
public interface MfaSecretCipher {

    /** @return {@code <key version>:<nonce>:<ciphertext>} for the active key */
    String encrypt(String plaintextSecret);

    /**
     * @throws IllegalArgumentException when the value is not in the expected format or was
     *                                  encrypted with a key that is no longer configured
     */
    String decrypt(String encryptedSecret);
}
