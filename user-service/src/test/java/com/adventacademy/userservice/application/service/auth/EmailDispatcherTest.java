package com.adventacademy.userservice.application.service.auth;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.adventacademy.userservice.application.port.AuditTrailPort;
import com.adventacademy.userservice.application.port.EmailService;
import com.adventacademy.userservice.domain.model.AuditEvent;
import com.adventacademy.userservice.domain.model.AuditEventType;
import com.adventacademy.userservice.domain.model.AuditResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EmailDispatcherTest {

    private static final String TOKEN = "super-secret-token-value";

    @Mock private EmailService emailService;
    @Mock private AuditTrailPort auditTrail;

    private EmailDispatcher dispatcher;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        dispatcher = new EmailDispatcher(emailService, auditTrail, Clock.fixed(Instant.parse("2026-05-28T10:00:00Z"), ZoneOffset.UTC));
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(EmailDispatcher.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(EmailDispatcher.class)).detachAppender(logs);
    }

    @Test
    void outsideATransactionTheMailGoesOutImmediately() {
        UUID userId = UUID.randomUUID();

        dispatcher.sendPasswordReset(userId, "a@b.c", TOKEN);

        verify(emailService).sendPasswordReset("a@b.c", TOKEN);
        verifyNoInteractions(auditTrail);
    }

    @Test
    void deliveryFailureIsAuditedWithoutTheTokenAndDoesNotFailTheCaller() {
        UUID userId = UUID.randomUUID();
        doThrow(new EmailService.SendEmailException("SMTP said no: " + TOKEN)).when(emailService).sendEmailVerification("a@b.c", TOKEN);

        dispatcher.sendEmailVerification(userId, "a@b.c", TOKEN);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditTrail).record(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo(AuditEventType.EMAIL_DELIVERY_FAILED);
        assertThat(event.getResult()).isEqualTo(AuditResult.FAILED);
        assertThat(event.getTargetUserId()).isEqualTo(userId);
        assertThat(event.getReason()).doesNotContain(TOKEN).contains("email_verification");

        assertThat(logs.list).isNotEmpty()
                .allSatisfy(line -> assertThat(line.getFormattedMessage()).doesNotContain(TOKEN).doesNotContain("a@b.c"));
    }
}
