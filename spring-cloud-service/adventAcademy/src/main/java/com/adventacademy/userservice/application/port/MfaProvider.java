package com.adventacademy.userservice.application.port;

public interface MfaProvider {
    String generateSecret();
    String generateQrCodeUrl(String email, String secret);
    boolean validateCode(String secret, String code);
}
