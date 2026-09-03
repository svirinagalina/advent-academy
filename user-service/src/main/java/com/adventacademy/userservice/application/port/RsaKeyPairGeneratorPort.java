package com.adventacademy.userservice.application.port;

import com.adventacademy.userservice.domain.model.RsaKeyPair;

/**
 * Creates fresh RSA key pairs for JWT signing.
 * <p>
 * The application layer depends on this port instead of a concrete generator so that the JDK
 * crypto details stay in the infrastructure layer, as the Clean Architecture rules require.
 */
public interface RsaKeyPairGeneratorPort {

    RsaKeyPair generateNewKeyPair();
}
