package com.adventacademy.userservice.adapter.rest.converter;

import com.adventacademy.userservice.adapter.rest.dto.JwkDTO;
import com.adventacademy.userservice.domain.model.RsaKeyPair;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Slf4j
@Component
public class RsaKeyToJwkConverter {

    public JwkDTO convert(RsaKeyPair keyPair) {
        try {
            PublicKey publicKey = loadPublicKeyFromPem(keyPair.getPublicKeyPem());

            if (!(publicKey instanceof RSAPublicKey rsaPublicKey)) {
                throw new IllegalArgumentException("Key is not an RSA public key");
            }

            String modulus = encodeBase64Url(rsaPublicKey.getModulus());
            String exponent = encodeBase64Url(rsaPublicKey.getPublicExponent());

            return new JwkDTO(
                    "RSA",
                    "sig",
                    keyPair.getKid(),
                    "RS256",
                    modulus,
                    exponent
            );
        } catch (Exception e) {
            log.error("Failed to convert RSA key to JWK for kid: {}", keyPair.getKid(), e);
            throw new RuntimeException("Failed to convert RSA key to JWK", e);
        }
    }

    private PublicKey loadPublicKeyFromPem(String pemKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
        String publicKeyPem = pemKey
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decodedKey = Base64.getDecoder().decode(publicKeyPem);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decodedKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
    }

    private String encodeBase64Url(BigInteger number) {
        byte[] bytes = number.toByteArray();
        // Remove leading zero byte if present (added by BigInteger.toByteArray())
        if (bytes[0] == 0) {
            byte[] temp = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, temp, 0, temp.length);
            bytes = temp;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
