package com.adventacademy.contentservice.application.service;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AssignmentAuthoringService {
    private final JdbcTemplate jdbc;
    private final ContentAccessGuard guard;
    public AssignmentAuthoringService(JdbcTemplate jdbc, ContentAccessGuard guard) { this.jdbc = jdbc; this.guard = guard; }
    public record Configuration(boolean required, String title, String description, long version) { }
    public record Edit(@NotNull Boolean required, @Size(max = 255) String title,
                       @Size(max = 20000) String description, @NotNull @Min(0) Long version) { }

    @Transactional(readOnly = true)
    public Configuration get(UUID lessonId) {
        authorize(lessonId, false);
        return read(lessonId);
    }

    public Configuration save(UUID lessonId, Edit input) {
        // Lock the parent before its lesson, matching publication's parent-to-child lock order.
        authorize(lessonId, true);
        jdbc.queryForList("SELECT id FROM content.lessons WHERE id=? FOR UPDATE", lessonId);
        Configuration previous = read(lessonId);
        if (previous.version() != input.version()) { throw new IllegalStateException("Assignment version changed; reload it"); }
        String title = input.title() == null ? "" : input.title().strip();
        String description = input.description() == null ? "" : input.description().strip();
        if (input.required() && (title.isBlank() || description.isBlank())) {
            throw new IllegalArgumentException("Assignment title and description are required");
        }
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM content.assignment_submissions WHERE lesson_id=?)",
                Boolean.class, lessonId))) { throw new IllegalStateException("An assignment with submissions cannot be changed"); }
        jdbc.update("UPDATE content.lessons SET required_assignment=?,assignment_revision=assignment_revision+1 WHERE id=?", input.required(), lessonId);
        if (input.required()) {
            jdbc.update("INSERT INTO content.lesson_assignments(lesson_id,title,description) VALUES(?,?,?) "
                    + "ON CONFLICT(lesson_id) DO UPDATE SET title=EXCLUDED.title,description=EXCLUDED.description,updated_at=CURRENT_TIMESTAMP",
                    lessonId, title, description);
        } else {
            jdbc.update("DELETE FROM content.lesson_assignments WHERE lesson_id=?", lessonId);
        }
        guard.recordSuccess("ASSIGNMENT_ACCESS", "Lesson", lessonId, "CONFIGURE_ASSIGNMENT");
        return read(lessonId);
    }

    private Configuration read(UUID id) {
        return jdbc.query("SELECT l.required_assignment,l.assignment_revision,a.title,a.description FROM content.lessons l "
                + "LEFT JOIN content.lesson_assignments a ON a.lesson_id=l.id WHERE l.id=?", (r,n) ->
                new Configuration(r.getBoolean(1), r.getString(3), r.getString(4), r.getLong(2)), id).getFirst();
    }

    private void authorize(UUID id, boolean writing) {
        var rows = jdbc.queryForList("SELECT c.id,c.owner_id,c.status FROM content.components c JOIN content.lessons l "
                + "ON l.component_id=c.id WHERE l.id=?" + (writing ? " FOR SHARE OF c" : ""), id);
        if (rows.isEmpty()) { throw new NotFoundException("Lesson not found"); }
        var row = rows.getFirst();
        guard.requireCurator("ASSIGNMENT_ACCESS", "Component", (UUID) row.get("id"), (UUID) row.get("owner_id"), "AUTHOR_ASSIGNMENT");
        if (writing && !"DRAFT".equals(row.get("status"))) { throw new IllegalStateException("Assignment structure is frozen after publication"); }
    }
}
