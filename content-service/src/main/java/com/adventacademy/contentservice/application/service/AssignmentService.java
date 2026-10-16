package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.application.port.CurrentUserProvider;
import com.adventacademy.contentservice.domain.constructor.PublicationStatus;
import com.adventacademy.contentservice.domain.repository.ComponentRepository;
import com.adventacademy.contentservice.domain.repository.CourseRepository;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class AssignmentService {
    private final JdbcTemplate jdbc;
    private final LearningService learning;
    private final CurrentUserProvider user;
    private final ContentAccessGuard guard;
    private final CourseRepository courses;
    private final ComponentRepository components;

    public AssignmentService(JdbcTemplate jdbc, LearningService learning, CurrentUserProvider user,
                             ContentAccessGuard guard, CourseRepository courses, ComponentRepository components) {
        this.jdbc = jdbc; this.learning = learning; this.user = user;
        this.guard = guard; this.courses = courses; this.components = components;
    }

    public record Definition(@NotBlank @Size(max = 255) String title,
                             @NotBlank @Size(max = 20000) String description) { }
    public record Submit(@NotNull UUID requestId, @NotBlank @Size(max = 2048) String url,
                         @NotNull @Size(max = 4000) String comment) { }
    public record Review(@NotNull @Min(0) Long version, @NotBlank @Pattern(regexp = "changes|accepted") String status,
                         @NotNull @Size(max = 4000) String feedback) { }
    public record Attempt(UUID id, UUID userId, UUID courseId, UUID lessonId, int attempt, String url,
                          String comment, String status, String feedback, UUID reviewerId,
                          Instant submittedAt, Instant reviewedAt, long version) { }
    public record AssignmentView(String title, String description, List<Attempt> attempts) { }
    public record Summary(UUID courseId, String courseTitle, UUID lessonId, String lessonTitle,
                          String title, String status, boolean available, boolean configured, int attempts) { }
    public record ReviewItem(Attempt submission, String courseTitle, String lessonTitle, String title, String description) { }
    public record ReviewPage(List<ReviewItem> items, long total, int page, int size) { }
    private static final String SELECT = "SELECT s.* FROM content.assignment_submissions s ";

    public void define(UUID lessonId, Definition definition) {
        var component = components.findByLessonId(lessonId).orElseThrow(() -> new NotFoundException("Lesson not found"));
        guard.requireCurator("ASSIGNMENT_ACCESS", "Component", component.getId(), component.getOwnerId(), "DEFINE_ASSIGNMENT");
        if (component.getStatus() == PublicationStatus.ARCHIVED) { throw new IllegalStateException("Component is archived"); }
        lockLesson(lessonId);
        Boolean mandatory = jdbc.queryForObject("SELECT required_assignment FROM content.lessons WHERE id=?", Boolean.class, lessonId);
        if (!Boolean.TRUE.equals(mandatory)) { throw new IllegalStateException("Set requiredAssignment before defining an assignment"); }
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM content.assignment_submissions WHERE lesson_id=?)", Boolean.class, lessonId))) {
            throw new IllegalStateException("An assignment with submissions cannot be changed");
        }
        jdbc.update("INSERT INTO content.lesson_assignments(lesson_id,title,description) VALUES (?,?,?) "
                + "ON CONFLICT (lesson_id) DO UPDATE SET title=EXCLUDED.title,description=EXCLUDED.description,updated_at=CURRENT_TIMESTAMP",
                lessonId, definition.title().trim(), definition.description().trim());
        jdbc.update("UPDATE content.lessons SET assignment_revision=assignment_revision+1 WHERE id=?", lessonId);
        guard.recordSuccess("ASSIGNMENT_ACCESS", "Lesson", lessonId, "DEFINE_ASSIGNMENT");
    }

    public AssignmentView assignment(UUID courseId, UUID lessonId) {
        var lesson = learning.lesson(courseId, lessonId);
        if (!lesson.assignment()) { throw new NotFoundException("Lesson has no mandatory assignment"); }
        Definition definition = definition(lessonId);
        return new AssignmentView(definition.title(), definition.description(), attempts(user.requireCurrentUserId(), courseId, lessonId));
    }

    @Transactional(readOnly = true)
    public List<Summary> list() {
        UUID student = user.requireCurrentUserId();
        var definitions = new java.util.HashMap<UUID, Definition>();
        jdbc.query("SELECT a.lesson_id,a.title,a.description FROM content.lesson_assignments a "
                + "WHERE EXISTS(SELECT 1 FROM content.lessons l JOIN content.course_components cc ON cc.component_id=l.component_id "
                + "JOIN content.learning_enrollments e ON e.course_id=cc.course_id WHERE l.id=a.lesson_id AND e.user_id=?)",
                (org.springframework.jdbc.core.RowCallbackHandler) r -> definitions.put(r.getObject(1,UUID.class), new Definition(r.getString(2),r.getString(3))), student);
        record State(String status, int count) { }
        var attempts = new java.util.HashMap<String, State>();
        jdbc.query("SELECT DISTINCT ON(course_id,lesson_id) course_id,lesson_id,status, "
                + "count(*) OVER(PARTITION BY course_id,lesson_id) AS attempts FROM content.assignment_submissions "
                + "WHERE user_id=? ORDER BY course_id,lesson_id,attempt DESC",
                (org.springframework.jdbc.core.RowCallbackHandler) r -> attempts.put(r.getObject(1)+":"+r.getObject(2),
                        new State(r.getString(3),r.getInt(4))), student);
        return learning.list().stream().flatMap(course -> course.modules().stream().flatMap(module -> module.lessons().stream())
                .filter(LearningService.LessonNode::assignment).map(lesson -> {
                    Definition definition = definitions.get(lesson.id());
                    State state = attempts.get(course.id()+":"+lesson.id());
                    return new Summary(course.id(),course.title(),lesson.id(),lesson.title(),
                            definition == null ? lesson.title() : definition.title(), state == null ? "draft" : state.status(),
                            lesson.available(), definition != null, state == null ? 0 : state.count());
                })).toList();
    }

    public AssignmentView submit(UUID courseId, UUID lessonId, Submit request) {
        validateUrl(request.url());
        learning.requireEnrollment(courseId, user.requireCurrentUserId(), true);
        var lesson = learning.lesson(courseId, lessonId);
        if (!lesson.assignment()) { throw new IllegalStateException("Lesson has no mandatory assignment"); }
        lockLesson(lessonId); // serializes the definition freeze against editor changes
        Definition definition = definition(lessonId);
        List<Attempt> previous = attempts(user.requireCurrentUserId(), courseId, lessonId);
        List<Attempt> retry = jdbc.query(SELECT + "WHERE user_id=? AND course_id=? AND lesson_id=? AND request_id=?",
                this::attempt, user.requireCurrentUserId(), courseId, lessonId, request.requestId());
        if (!retry.isEmpty()) {
            if (!retry.get(0).url().equals(request.url()) || !retry.get(0).comment().equals(request.comment())) {
                throw new IllegalStateException("RequestId was already used with different content");
            }
            return new AssignmentView(definition.title(), definition.description(), previous);
        }
        if (!previous.isEmpty() && !"changes".equals(previous.get(previous.size() - 1).status())) {
            throw new IllegalStateException("Wait for review or requested changes before resubmitting");
        }
        if (learning.course(courseId).modules().stream().flatMap(module -> module.lessons().stream())
                .anyMatch(node -> node.id().equals(lessonId) && node.completed())) {
            throw new IllegalStateException("Lesson is already completed");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO content.assignment_submissions(id,user_id,course_id,lesson_id,request_id,attempt,url,comment,status) "
                + "VALUES (?,?,?,?,?,?,?,?,'review')", id, user.requireCurrentUserId(), courseId, lessonId,
                request.requestId(), previous.size() + 1, request.url(), request.comment());
        guard.recordSuccess("ASSIGNMENT_ACCESS", "Submission", id, "SUBMIT_ASSIGNMENT");
        return new AssignmentView(definition.title(), definition.description(), attempts(user.requireCurrentUserId(), courseId, lessonId));
    }

    @Transactional(readOnly = true)
    public ReviewPage queue(String status, int page, int size) {
        requireTeacher();
        if (!List.of("review", "changes", "accepted").contains(status)) { throw new IllegalArgumentException("Invalid status"); }
        String where = " FROM content.assignment_submissions s JOIN content.courses c ON c.id=s.course_id "
                + "JOIN content.lessons l ON l.id=s.lesson_id JOIN content.lesson_assignments a ON a.lesson_id=l.id "
                + "WHERE s.status=? AND c.status='PUBLISHED' AND s.user_id<>? "
                + (guard.isCurator() ? "" : "AND c.owner_id=? ");
        var args = new java.util.ArrayList<Object>();
        args.add(status); args.add(user.requireCurrentUserId());
        if (!guard.isCurator()) { args.add(user.requireCurrentUserId()); }
        Long total = jdbc.queryForObject("SELECT count(*)" + where, Long.class, args.toArray());
        args.add(size); args.add((long) page * size);
        List<ReviewItem> items = jdbc.query("SELECT s.*,c.title AS course_title,l.title AS lesson_title,a.title AS assignment_title,"
                + "a.description AS assignment_description" + where + "ORDER BY s.submitted_at,s.id LIMIT ? OFFSET ?",
                (r,n) -> new ReviewItem(attempt(r,n),r.getString("course_title"),r.getString("lesson_title"),
                        r.getString("assignment_title"),r.getString("assignment_description")), args.toArray());
        return new ReviewPage(items, total == null ? 0 : total, page, size);
    }

    @Transactional(readOnly = true)
    public ReviewItem detail(UUID submissionId) {
        Attempt attempt = find(submissionId, false);
        authorizeReview(attempt);
        return item(attempt);
    }

    public ReviewItem review(UUID submissionId, Review request) {
        Attempt snapshot = find(submissionId, false);
        authorizeReview(snapshot);
        learning.requireEnrollment(snapshot.courseId(), snapshot.userId(), true);
        Attempt current = find(submissionId, true);
        String feedback = request.feedback().trim();
        if ("changes".equals(request.status()) && feedback.isBlank()) {
            throw new IllegalArgumentException("Explain the requested changes");
        }
        if (!"review".equals(current.status())) {
            if (current.status().equals(request.status()) && Objects.equals(current.feedback(), feedback)
                    && user.requireCurrentUserId().equals(current.reviewerId()) && current.version() == request.version() + 1) {
                return item(current);
            }
            throw new IllegalStateException("Submission was already reviewed; reload it");
        }
        if (current.version() != request.version()) { throw new IllegalStateException("Submission version changed; reload it"); }
        var course = learning.courseForStudent(current.courseId(), current.userId());
        var lesson = course.modules().stream().flatMap(module -> module.lessons().stream())
                .filter(node -> node.id().equals(current.lessonId())).findFirst()
                .orElseThrow(() -> new NotFoundException("Lesson is no longer in course"));
        if (!lesson.available()) { throw new IllegalStateException("Complete the previous lesson first"); }
        if ("accepted".equals(request.status())) {
            jdbc.update("INSERT INTO content.learning_completions(user_id,course_id,lesson_id) VALUES (?,?,?) "
                    + "ON CONFLICT (user_id,course_id,lesson_id) DO NOTHING", current.userId(), current.courseId(), current.lessonId());
        }
        jdbc.update("UPDATE content.assignment_submissions SET status=?,feedback=?,reviewer_id=?,reviewed_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?",
                request.status(), feedback, user.requireCurrentUserId(), submissionId);
        guard.recordSuccess("ASSIGNMENT_ACCESS", "Submission", submissionId, "REVIEW_ASSIGNMENT");
        return item(find(submissionId, false));
    }

    private void requireTeacher() {
        if (!user.hasAnyRole("ADMIN", "CONTENT_EDITOR", "INSTRUCTOR")) { throw new AccessDeniedException("Teacher role required"); }
    }
    private void authorizeReview(Attempt attempt) {
        requireTeacher();
        if (user.requireCurrentUserId().equals(attempt.userId())) { throw new AccessDeniedException("Self review is forbidden"); }
        var course = courses.findById(attempt.courseId()).orElseThrow(() -> new NotFoundException("Course not found"));
        guard.requireCurator("ASSIGNMENT_ACCESS", "Course", course.getId(), course.getOwnerId(), "REVIEW_ASSIGNMENT");
        if (course.getStatus() != PublicationStatus.PUBLISHED) { throw new AccessDeniedException("Course is not published"); }
    }
    private ReviewItem item(Attempt attempt) {
        String courseTitle = jdbc.queryForObject("SELECT title FROM content.courses WHERE id=?", String.class, attempt.courseId());
        String lessonTitle = jdbc.queryForObject("SELECT title FROM content.lessons WHERE id=?", String.class, attempt.lessonId());
        Definition definition = definition(attempt.lessonId());
        return new ReviewItem(attempt, courseTitle, lessonTitle, definition.title(), definition.description());
    }
    private List<Definition> definitions(UUID lessonId) {
        return jdbc.query("SELECT title,description FROM content.lesson_assignments WHERE lesson_id=?",
                (rs, n) -> new Definition(rs.getString("title"), rs.getString("description")), lessonId);
    }
    private Definition definition(UUID lessonId) {
        return definitions(lessonId).stream().findFirst().orElseThrow(() -> new IllegalStateException("Assignment instructions have not been added"));
    }
    private List<Attempt> attempts(UUID userId, UUID courseId, UUID lessonId) {
        return jdbc.query(SELECT + "WHERE user_id=? AND course_id=? AND lesson_id=? ORDER BY attempt",
                this::attempt, userId, courseId, lessonId);
    }
    private Attempt find(UUID id, boolean lock) {
        return jdbc.query(SELECT + "WHERE s.id=?" + (lock ? " FOR UPDATE" : ""), this::attempt, id)
                .stream().findFirst().orElseThrow(() -> new NotFoundException("Submission not found"));
    }
    private Attempt attempt(ResultSet rs, int n) throws SQLException {
        var reviewed = rs.getTimestamp("reviewed_at");
        return new Attempt(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getObject("course_id", UUID.class), rs.getObject("lesson_id", UUID.class), rs.getInt("attempt"),
                rs.getString("url"), rs.getString("comment"), rs.getString("status"), rs.getString("feedback"),
                rs.getObject("reviewer_id", UUID.class), rs.getTimestamp("submitted_at").toInstant(),
                reviewed == null ? null : reviewed.toInstant(), rs.getLong("version"));
    }
    private void lockLesson(UUID lessonId) {
        if (jdbc.queryForList("SELECT id FROM content.lessons WHERE id=? FOR UPDATE", UUID.class, lessonId).isEmpty()) {
            throw new NotFoundException("Lesson not found");
        }
    }
    private void validateUrl(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Use an absolute HTTPS link without credentials");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Use an absolute HTTPS link without credentials", exception);
        }
    }
}
