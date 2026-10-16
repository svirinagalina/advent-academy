package com.adventacademy.contentservice.application.service;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnrollmentWriter {
    private final JdbcTemplate jdbc;
    private final ContentAccessGuard guard;
    public EnrollmentWriter(JdbcTemplate jdbc, ContentAccessGuard guard) { this.jdbc = jdbc; this.guard = guard; }

    @Transactional
    public void assignInstructor(UUID courseId, UUID instructorId, long version) {
        if (!guard.isCurator()) { throw new org.springframework.security.access.AccessDeniedException("Curator role required"); }
        var rows = jdbc.queryForList("SELECT owner_id,status,version FROM content.courses WHERE id=? FOR UPDATE", courseId);
        if (rows.isEmpty()) { throw new NotFoundException("Course not found"); }
        var row = rows.getFirst();
        if (!"PUBLISHED".equals(row.get("status"))) { throw new IllegalStateException("Only a published course can be delegated"); }
        if (((Number) row.get("version")).longValue() != version) { throw new IllegalStateException("Course version changed; reload it"); }
        UUID owner = instructorId == null ? guard.requireCurrentUserId() : instructorId;
        jdbc.update("UPDATE content.courses SET owner_id=?,version=version+1,updated_at=now() WHERE id=?", owner, courseId);
        guard.recordSuccess("COURSE_ACCESS", "Course", courseId, "ASSIGN_INSTRUCTOR:" + row.get("owner_id") + "->" + owner);
    }

    @Transactional
    public void assign(UUID courseId, UUID userId) {
        var rows = jdbc.queryForList("SELECT owner_id,status FROM content.courses WHERE id=? FOR SHARE", courseId);
        if (rows.isEmpty()) { throw new NotFoundException("Course not found"); }
        var row = rows.getFirst();
        guard.requireCurator("COURSE_ACCESS", "Course", courseId, (UUID) row.get("owner_id"), "ASSIGN_COURSE");
        if (!"PUBLISHED".equals(row.get("status"))) { throw new IllegalStateException("Course is not published"); }
        jdbc.update("INSERT INTO content.learning_enrollments(user_id,course_id) VALUES (?,?) "
                + "ON CONFLICT (user_id,course_id) DO NOTHING", userId, courseId);
    }
}
