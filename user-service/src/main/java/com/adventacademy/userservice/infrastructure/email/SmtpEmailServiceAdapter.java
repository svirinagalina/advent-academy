package com.adventacademy.userservice.infrastructure.email;

import com.adventacademy.userservice.application.port.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "spring.mail.enabled", havingValue = "true", matchIfMissing = true)
public class SmtpEmailServiceAdapter implements EmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String baseUrl;

    public SmtpEmailServiceAdapter(
            JavaMailSender mailSender,
            @Value("${app.email.from:noreply@adventacademy.com}") String fromAddress,
            @Value("${app.baseurl:https://adventacademy.com}") String baseUrl
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.baseUrl = baseUrl;
    }

    @Override
    public void sendEmailVerification(String email, String verificationToken) {
        try {
            String verifyLink = baseUrl + "/auth/confirm-email?token=" + verificationToken;
            String subject = "Verify your email - Advent Academy";
            String body = "Welcome to Advent Academy!\n\n" +
                    "Please verify your email by clicking the link below:\n" +
                    verifyLink + "\n\n" +
                    "This link expires in 24 hours.\n\n" +
                    "If you didn't create this account, please ignore this email.";

            sendEmail(email, subject, body);
            log.info("Email verification sent to {}", email);
        } catch (Exception e) {
            log.error("Failed to send email verification to {}", email, e);
            throw new SendEmailException("Failed to send email verification", e);
        }
    }

    @Override
    public void sendInvitation(String email, String token) {
        sendEmail(email, "Приглашение в Advent Academy",
                "Вам создана учётная запись Advent Academy. Установите пароль и подтвердите email по ссылке:\n\n"
                + baseUrl + "/auth/accept-invitation#token=" + token
                + "\n\nСсылка действует 24 часа. Если приглашение отправлено по ошибке, проигнорируйте письмо.");
    }

    @Override
    public void sendPasswordReset(String email, String resetToken) {
        try {
            String resetLink = baseUrl + "/auth/reset-password?token=" + resetToken;
            String subject = "Reset your password - Advent Academy";
            String body = "We received a request to reset your password.\n\n" +
                    "Click the link below to reset your password:\n" +
                    resetLink + "\n\n" +
                    "This link expires in 1 hour.\n\n" +
                    "If you didn't request this, please ignore this email.\n" +
                    "Your password will not change until you complete the reset.";

            sendEmail(email, subject, body);
            log.info("Password reset email sent to {}", email);
        } catch (Exception e) {
            log.error("Failed to send password reset to {}", email, e);
            throw new SendEmailException("Failed to send password reset email", e);
        }
    }

    @Override
    public void sendRecoveryCodes(String email, List<String> recoveryCodes) {
        try {
            String subject = "Your MFA Recovery Codes - Advent Academy";
            StringBuilder body = new StringBuilder();
            body.append("You've enabled two-factor authentication on your Advent Academy account.\n\n");
            body.append("Save these recovery codes in a secure place. Each code can be used once to regain access if you lose your authenticator.\n\n");
            body.append("Recovery Codes:\n");
            for (String code : recoveryCodes) {
                body.append("  ").append(code).append("\n");
            }
            body.append("\nDo not share these codes with anyone.\n");
            body.append("If you didn't enable MFA, please contact support immediately.");

            sendEmail(email, subject, body.toString());
            log.info("Recovery codes sent to {}", email);
        } catch (Exception e) {
            log.error("Failed to send recovery codes to {}", email, e);
            throw new SendEmailException("Failed to send recovery codes", e);
        }
    }

    private void sendEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
