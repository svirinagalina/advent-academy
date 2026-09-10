package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.application.port.AuditTrailPort;
import com.adventacademy.contentservice.application.port.CurrentUserProvider;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Object-level authorization shared by the content use-cases.
 * <p>
 * A role alone never decides whether the caller may touch a concrete object: content may be
 * curated by its owner, by an {@code ADMIN} and by a {@code CONTENT_EDITOR}, so an
 * {@code INSTRUCTOR} only ever edits the components, directions, lessons and assets they created
 * themselves. Both refusals and successful state changes are written to the audit trail, so a
 * probing attempt and a real publication are equally reconstructable from
 * {@code content_audit_events}.
 * <p>
 * Objects whose {@code ownerId} is {@code null} predate ownership and stay curatable by curators
 * only.
 */
@Component
public class ContentAccessGuard {

    /** Roles that may curate any object, no matter who created it. */
    private static final String[] CURATOR_ROLES = {"ADMIN", "CONTENT_EDITOR"};

    private static final String RESULT_DENIED = "DENIED";
    private static final String RESULT_SUCCESS = "SUCCESS";

    private final CurrentUserProvider currentUserProvider;
    private final AuditTrailPort auditTrail;

    public ContentAccessGuard(CurrentUserProvider currentUserProvider, AuditTrailPort auditTrail) {
        this.currentUserProvider = currentUserProvider;
        this.auditTrail = auditTrail;
    }

    public boolean isCurator() {
        return currentUserProvider.hasAnyRole(CURATOR_ROLES);
    }

    public boolean isOwnedByCurrentUser(UUID ownerId) {
        return ownerId != null && currentUserProvider.currentUserId()
                .map(ownerId::equals)
                .orElse(false);
    }

    /** @return {@code true} when the caller may modify an object owned by {@code ownerId}. */
    public boolean mayCurate(UUID ownerId) {
        return isCurator() || isOwnedByCurrentUser(ownerId);
    }

    public UUID requireCurrentUserId() {
        return currentUserProvider.requireCurrentUserId();
    }

    /**
     * Allows the operation for the owner and for curators, and records the refusal otherwise.
     *
     * @throws ContentAccessDeniedException when the object belongs to somebody else
     */
    public void requireCurator(String eventType, String resourceType, UUID resourceId, UUID ownerId, String operation) {
        if (mayCurate(ownerId)) {
            return;
        }
        recordDenial(eventType, resourceType, resourceId, operation,
                resourceType.toLowerCase() + " belongs to another user");
        throw new ContentAccessDeniedException(resourceType, resourceId);
    }

    public void recordDenial(String eventType, String resourceType, UUID resourceId, String operation, String reason) {
        record(eventType, operation, target(resourceType, resourceId), RESULT_DENIED, reason);
    }

    public void recordSuccess(String eventType, String resourceType, UUID resourceId, String operation) {
        record(eventType, operation, target(resourceType, resourceId), RESULT_SUCCESS, null);
    }

    private void record(String eventType, String operation, String target, String result, String reason) {
        auditTrail.record(new AuditTrailPort.AuditEvent(
                eventType,
                currentUserProvider.currentUserId().orElse(null),
                operation,
                target,
                result,
                reason));
    }

    private static String target(String resourceType, UUID resourceId) {
        return resourceType.toLowerCase() + ":" + resourceId;
    }
}
