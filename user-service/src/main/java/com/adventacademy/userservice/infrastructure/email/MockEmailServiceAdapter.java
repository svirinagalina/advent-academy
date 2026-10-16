package com.adventacademy.userservice.infrastructure.email;

import com.adventacademy.userservice.application.port.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Mock email service for testing and local development.
 * Stores emails in memory instead of sending them.
 * Activated when spring.mail.enabled=false.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "spring.mail.enabled", havingValue = "false")
public class MockEmailServiceAdapter implements EmailService {

    private static final List<MockEmail> sentEmails = new ArrayList<>();

    public record MockEmail(String to, String subject, String body, long timestamp) {}

    @Override
    public void sendInvitation(String email, String token) {
        logMockEmail(email, "Приглашение в Advent Academy", "Invitation token: " + token);
    }

    @Override
    public void sendEmailVerification(String email, String verificationToken) {
        String subject = "Verify your email - Advent Academy";
        String body = "Verification token: " + verificationToken;
        logMockEmail(email, subject, body);
    }

    @Override
    public void sendPasswordReset(String email, String resetToken) {
        String subject = "Reset your password - Advent Academy";
        String body = "Password reset token: " + resetToken;
        logMockEmail(email, subject, body);
    }

    @Override
    public void sendRecoveryCodes(String email, List<String> recoveryCodes) {
        String subject = "Your MFA Recovery Codes - Advent Academy";
        String body = "Recovery codes: " + String.join(", ", recoveryCodes);
        logMockEmail(email, subject, body);
    }

    private void logMockEmail(String to, String subject, String body) {
        MockEmail email = new MockEmail(to, subject, body, System.currentTimeMillis());
        sentEmails.add(email);
        log.info("[MOCK EMAIL] To: {}, Subject: {}", to, subject);
    }

    // Test helpers
    public static List<MockEmail> getSentEmails() {
        return new ArrayList<>(sentEmails);
    }

    public static void clearSentEmails() {
        sentEmails.clear();
    }

    public static MockEmail getLastEmail() {
        return sentEmails.isEmpty() ? null : sentEmails.get(sentEmails.size() - 1);
    }

    public static List<MockEmail> getEmailsTo(String email) {
        return sentEmails.stream()
                .filter(e -> e.to.equals(email))
                .toList();
    }
}
