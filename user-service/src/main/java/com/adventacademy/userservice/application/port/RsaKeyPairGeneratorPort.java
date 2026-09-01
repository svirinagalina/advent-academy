package com.adventacademy.userservice.application.port;

import com.adventacademy.userservice.domain.model.RsaKeyPair;

/**
 * Outbound port for creating new RSA signing key pairs, so that the application
 * layer does not depend on an infrastructure implementation.
 */
public interface RsaKeyPairGeneratorPort {

    RsaKeyPair generateNewKeyPair();
}
