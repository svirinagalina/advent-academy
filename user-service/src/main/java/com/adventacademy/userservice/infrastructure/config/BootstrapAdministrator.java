package com.adventacademy.userservice.infrastructure.config;

import com.adventacademy.userservice.application.port.PasswordEncoder;
import com.adventacademy.userservice.application.service.admin.UserAdminPolicy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Explicit local/operator bootstrap; never accepts passwords via HTTP, command arguments or logs. */
@Component
@ConditionalOnProperty(name = "bootstrap.enabled", havingValue = "true")
public class BootstrapAdministrator implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final UserAdminPolicy policy;
    private final PasswordEncoder passwords;
    private final Environment environment;
    private final TransactionTemplate tx;
    public BootstrapAdministrator(JdbcTemplate jdbc, UserAdminPolicy policy, PasswordEncoder passwords,
                                  Environment environment, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.policy = policy; this.passwords = passwords; this.environment = environment;
        this.tx = new TransactionTemplate(manager);
    }
    @Override
    public void run(ApplicationArguments arguments) throws Exception {
        final String email = environment.getRequiredProperty("bootstrap.email").strip().toLowerCase(Locale.ROOT);
        if (!email.matches("[^\\s@]{1,200}@[^\\s@]{1,200}\\.[^\\s@]{1,40}") || email.length() > 255) {
            throw new IllegalArgumentException("Invalid bootstrap email");
        }
        final String password = Files.readString(Path.of(environment.getRequiredProperty("bootstrap.password-file")), StandardCharsets.UTF_8).strip();
        if (password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Bootstrap password must have 12 characters and at most 72 UTF-8 bytes");
        }
        final String hash = passwords.encode(password);
        tx.executeWithoutResult(status -> {
            policy.lock();
            if (jdbc.queryForObject("SELECT count(*) FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE r.name='ADMIN'", Long.class) > 0) {
                return;
            }
            final UUID user = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO users(id,email,password_hash,first_name,last_name,status,email_verified,mfa_enabled,
                    failed_login_attempts,created_at,updated_at) VALUES(?,?,?,'Platform','Administrator','ACTIVE',true,false,0,now(),now())
                    """, user, email, hash);
            jdbc.update("INSERT INTO user_roles SELECT ?,id FROM roles WHERE name='ADMIN'", user);
            policy.recordRoleChange(user, List.of(), List.of("ADMIN"));
        });
    }
}
