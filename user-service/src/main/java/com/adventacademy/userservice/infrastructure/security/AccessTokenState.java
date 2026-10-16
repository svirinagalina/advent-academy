package com.adventacademy.userservice.infrastructure.security;

import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

/** Authoritative state; never cache a successful check across requests. */
@Component
public class AccessTokenState {
    private final EntityManager entities;

    public AccessTokenState(EntityManager entities) {
        this.entities = entities;
    }

    public void lock(UUID userId) {
        entities.createNativeQuery("SELECT id FROM users WHERE id=?1 FOR UPDATE")
                .setParameter(1, userId).getResultList();
    }

    public long version(UUID userId) {
        // Native queries through JPA flush pending status/role changes before reading
        // the trigger-maintained version, including login after a temporary lock.
        return ((Number) entities.createNativeQuery("SELECT authorization_version FROM users WHERE id=?1")
                .setParameter(1, userId).getSingleResult()).longValue();
    }

    public boolean isActive(UUID userId, Long version) {
        return version != null && Boolean.TRUE.equals(entities.createNativeQuery("""
                SELECT EXISTS(SELECT 1 FROM users WHERE id=?1 AND authorization_version=?2
                AND email_verified=true AND status IN ('ACTIVE','LOCKED'))
                """).setParameter(1, userId).setParameter(2, version).getSingleResult());
    }
}
