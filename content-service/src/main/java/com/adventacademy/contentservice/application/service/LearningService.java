package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.application.port.CurrentUserProvider;
import com.adventacademy.contentservice.application.port.EnrollmentUserPort;
import com.adventacademy.contentservice.domain.ContentStatus;
import com.adventacademy.contentservice.domain.LessonContent;
import com.adventacademy.contentservice.domain.constructor.PublicationStatus;
import com.adventacademy.contentservice.domain.repository.ComponentRepository;
import com.adventacademy.contentservice.domain.repository.CourseRepository;
import com.adventacademy.contentservice.domain.repository.LessonContentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Learner read/progress boundary. Enrollment locks serialize changes across application instances. */
@Service
@Transactional
public class LearningService {
    private final JdbcTemplate jdbc;
    private final CurrentUserProvider currentUser;
    private final CourseRepository courses;
    private final ComponentRepository components;
    private final LessonContentRepository contents;
    private final ContentAccessGuard guard;
    private final EnrollmentUserPort enrollmentUsers;

    public LearningService(JdbcTemplate jdbc, CurrentUserProvider currentUser, CourseRepository courses,
                           ComponentRepository components, LessonContentRepository contents, ContentAccessGuard guard,
                           EnrollmentUserPort enrollmentUsers) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.courses = courses;
        this.components = components;
        this.contents = contents;
        this.guard = guard;
        this.enrollmentUsers = enrollmentUsers;
    }

    public record LessonNode(UUID id, String title, int position, boolean assignment, boolean completed,
                             boolean available) { }
    public record ModuleNode(UUID id, String title, List<LessonNode> lessons) { }
    public record CourseView(UUID id, String title, String description, String status, int total,
                             int completed, UUID currentLessonId, List<ModuleNode> modules) { }
    public record AssetView(UUID id, String filename, String contentType, long fileSize, String url) { }
    public record LessonView(UUID id, String title, String html, boolean assignment, List<AssetView> assets) { }

    public record TeachingCourse(UUID id, String title) { }

    @Transactional(readOnly = true)
    public List<TeachingCourse> accessCourses() {
        return jdbc.query("SELECT c.id,c.title FROM content.courses c JOIN content.learning_enrollments e ON e.course_id=c.id "
                + "WHERE e.user_id=? AND c.status='PUBLISHED' ORDER BY c.id", (r,n) ->
                new TeachingCourse(r.getObject(1,UUID.class),r.getString(2)), currentUser.requireCurrentUserId());
    }

    @Transactional(readOnly = true)
    public TeachingCourse accessCourse(UUID courseId) {
        var rows = jdbc.query("SELECT c.id,c.title FROM content.courses c JOIN content.learning_enrollments e ON e.course_id=c.id "
                + "WHERE e.user_id=? AND c.id=? AND c.status='PUBLISHED'", (r,n) ->
                new TeachingCourse(r.getObject(1,UUID.class),r.getString(2)), currentUser.requireCurrentUserId(), courseId);
        if (rows.isEmpty()) { throw new AccessDeniedException("Course is not assigned or published"); }
        return rows.getFirst();
    }

    @Transactional(readOnly = true)
    public List<TeachingCourse> teachingCourses() {
        boolean curator = guard.isCurator();
        return jdbc.query("SELECT id,title FROM content.courses WHERE status='PUBLISHED' "
                + (curator ? "" : "AND owner_id=? ") + "ORDER BY id", (r,n) ->
                new TeachingCourse(r.getObject(1,UUID.class),r.getString(2)),
                curator ? new Object[0] : new Object[]{currentUser.requireCurrentUserId()});
    }

    @Transactional(readOnly = true)
    public TeachingCourse teachingCourse(UUID courseId) {
        var course = courses.findById(courseId).orElseThrow(() -> new NotFoundException("Course not found"));
        guard.requireCurator("COURSE_ACCESS", "Course", courseId, course.getOwnerId(), "MANAGE_MEETINGS");
        if (course.getStatus() != PublicationStatus.PUBLISHED) { throw new org.springframework.security.access.AccessDeniedException("Course is not published"); }
        return new TeachingCourse(courseId, course.getTitle());
    }

    @Transactional(readOnly = true)
    public List<UUID> teachingStudents(UUID courseId) {
        var course = courses.findById(courseId).orElseThrow(() -> new NotFoundException("Course not found"));
        guard.requireCurator("COURSE_ACCESS", "Course", courseId, course.getOwnerId(), "READ_COURSE_ROSTER");
        if (course.getStatus() != PublicationStatus.PUBLISHED && course.getStatus() != PublicationStatus.ARCHIVED) {
            throw new AccessDeniedException("Course roster is available only for published or archived courses");
        }
        return jdbc.queryForList("SELECT user_id FROM content.learning_enrollments WHERE course_id=? ORDER BY user_id", UUID.class, courseId);
    }

    @org.springframework.beans.factory.annotation.Autowired
    private EnrollmentWriter enrollmentWriter;

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void assign(UUID courseId, UUID studentId, String authorization) {
        var course = courses.findById(courseId).orElseThrow(() -> new NotFoundException("Course not found"));
        guard.requireCurator("COURSE_ACCESS", "Course", courseId, course.getOwnerId(), "ASSIGN_COURSE");
        if (course.getStatus() != PublicationStatus.PUBLISHED) {
            throw new IllegalStateException("Course is not published");
        }
        enrollmentUsers.requireActiveUser(courseId, studentId, authorization);
        enrollmentWriter.assign(courseId, studentId);
    }

    @Transactional(readOnly = true)
    public List<CourseView> list() {
        UUID userId = currentUser.requireCurrentUserId();
        var result = new java.util.LinkedHashMap<UUID, CourseProgress>();
        jdbc.query("""
                SELECT c.id AS course_id,c.title AS course_title,c.description,e.started_at,
                       m.id AS module_id,m.title AS module_title,m.status AS module_status,
                       l.id AS lesson_id,l.title AS lesson_title,l.position,l.required_assignment,
                       done.lesson_id AS completed_lesson
                FROM content.learning_enrollments e JOIN content.courses c ON c.id=e.course_id
                LEFT JOIN content.course_components cc ON cc.course_id=c.id
                LEFT JOIN content.components m ON m.id=cc.component_id
                LEFT JOIN content.lessons l ON l.component_id=m.id
                LEFT JOIN content.learning_completions done ON done.user_id=e.user_id
                     AND done.course_id=c.id AND done.lesson_id=l.id
                WHERE e.user_id=? AND c.status='PUBLISHED'
                ORDER BY e.assigned_at,e.course_id,cc.position,l.position
                """, (org.springframework.jdbc.core.RowCallbackHandler) row -> {
                    UUID id = row.getObject("course_id",UUID.class);
                    CourseProgress progress = result.get(id);
                    if (progress == null) {
                        progress = new CourseProgress(id,row.getString("course_title"),row.getString("description"),row.getObject("started_at") != null);
                        result.put(id,progress);
                    }
                    progress.add(row);
                }, userId);
        return result.values().stream().map(CourseProgress::view).toList();
    }

    @Transactional(readOnly = true)
    public CourseView course(UUID courseId) {
        return courseForStudent(courseId, currentUser.requireCurrentUserId());
    }

    CourseView courseForStudent(UUID courseId, UUID studentId) {
        requireEnrollment(courseId, studentId, false);
        var course = courses.findById(courseId).orElseThrow(() -> new NotFoundException("Course not found"));
        if (course.getStatus() != PublicationStatus.PUBLISHED) {
            throw new AccessDeniedException("Course is not published");
        }
        java.util.Set<UUID> completed = new java.util.HashSet<>(jdbc.queryForList("SELECT lesson_id FROM content.learning_completions "
                + "WHERE user_id=? AND course_id=?", UUID.class, studentId, courseId));
        List<LessonRow> rows = jdbc.query("SELECT l.id, l.title, l.position, l.required_assignment, "
                        + "c.id AS module_id, c.title AS module_title, c.status AS module_status "
                        + "FROM content.course_components cc JOIN content.components c ON c.id=cc.component_id "
                        + "JOIN content.lessons l ON l.component_id=c.id WHERE cc.course_id=? ORDER BY cc.position, l.position",
                (rs, n) -> new LessonRow(rs.getObject("id", UUID.class), rs.getString("title"),
                        rs.getInt("position"), rs.getBoolean("required_assignment"), rs.getObject("module_id", UUID.class),
                        rs.getString("module_title"), rs.getString("module_status")), courseId);
        if (rows.stream().anyMatch(row -> !"PUBLISHED".equals(row.moduleStatus()))) {
            throw new IllegalStateException("Course contains an unpublished component");
        }
        var modules = new java.util.LinkedHashMap<UUID, java.util.ArrayList<LessonNode>>();
        var titles = new java.util.HashMap<UUID, String>();
        boolean previousDone = true;
        UUID current = null;
        int count = 0;
        for (var row : rows) {
            boolean done = completed.contains(row.id());
            if (done) { count++; }
            if (!done && current == null) { current = row.id(); }
            modules.computeIfAbsent(row.moduleId(), key -> new java.util.ArrayList<>())
                    .add(new LessonNode(row.id(), row.title(), row.position(), row.assignment(), done, previousDone));
            titles.put(row.moduleId(), row.moduleTitle());
            previousDone = previousDone && done;
        }
        boolean started = Boolean.TRUE.equals(jdbc.queryForObject("SELECT started_at IS NOT NULL "
                + "FROM content.learning_enrollments WHERE user_id=? AND course_id=?", Boolean.class,
                studentId, courseId));
        String status = !rows.isEmpty() && count == rows.size() ? "complete" : started ? "active" : "new";
        return new CourseView(courseId, course.getTitle(), course.getDescription(), status, rows.size(), count, current,
                modules.entrySet().stream().map(entry -> new ModuleNode(entry.getKey(), titles.get(entry.getKey()),
                        List.copyOf(entry.getValue()))).toList());
    }

    public LessonView lesson(UUID courseId, UUID lessonId) {
        requireEnrollment(courseId, true);
        LessonNode node = requireLesson(course(courseId), lessonId);
        requireAvailable(node);
        LessonContent content = contents.findByLessonId(lessonId)
                .filter(value -> value.getStatus() == ContentStatus.PUBLISHED)
                .orElseThrow(() -> new IllegalStateException("Lesson content is not published"));
        List<AssetView> assets = jdbc.query("SELECT id, filename, content_type, file_size FROM content.assets "
                        + "WHERE lesson_id=? AND created_at<=? ORDER BY filename, id",
                (rs, n) -> new AssetView(rs.getObject("id", UUID.class), rs.getString("filename"),
                        rs.getString("content_type"), rs.getLong("file_size"), "/api/assets/" + rs.getObject("id")), lessonId, java.sql.Timestamp.from(content.getUpdatedAt()));
        jdbc.update("UPDATE content.learning_enrollments SET started_at=COALESCE(started_at, CURRENT_TIMESTAMP) "
                + "WHERE user_id=? AND course_id=?", currentUser.requireCurrentUserId(), courseId);
        return new LessonView(lessonId, node.title(), content.getHtml(), node.assignment(), assets);
    }

    public CourseView complete(UUID courseId, UUID lessonId) {
        requireEnrollment(courseId, true);
        LessonNode lesson = requireLesson(course(courseId), lessonId);
        // A retry after successful completion is always successful, including assignment lessons.
        if (lesson.completed()) { return course(courseId); }
        requireAvailable(lesson);
        if (lesson.assignment()) { throw new IllegalStateException("Assignment approval is required"); }
        contents.findByLessonId(lessonId).filter(value -> value.getStatus() == ContentStatus.PUBLISHED)
                .orElseThrow(() -> new IllegalStateException("Lesson content is not published"));
        jdbc.update("INSERT INTO content.learning_completions(user_id, course_id, lesson_id) VALUES (?, ?, ?) "
                + "ON CONFLICT (user_id, course_id, lesson_id) DO NOTHING", currentUser.requireCurrentUserId(), courseId, lessonId);
        jdbc.update("UPDATE content.learning_enrollments SET started_at=COALESCE(started_at, CURRENT_TIMESTAMP) "
                + "WHERE user_id=? AND course_id=?", currentUser.requireCurrentUserId(), courseId);
        return course(courseId);
    }

    public void publishContent(UUID lessonId) {
        var component = components.findByLessonId(lessonId).orElseThrow(() -> new NotFoundException("Lesson not found"));
        guard.requireCurator("LESSON_CONTENT_ACCESS", "Component", component.getId(), component.getOwnerId(), "PUBLISH_LESSON_CONTENT");
        jdbc.queryForObject("SELECT id FROM content.lessons WHERE id=? FOR UPDATE", UUID.class, lessonId);
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM content.lesson_drafts WHERE lesson_id=?)",
                Boolean.class, lessonId))) {
            throw new IllegalStateException("Use the versioned draft publication API");
        }
        var content = contents.findByLessonId(lessonId).orElseThrow(() -> new NotFoundException("Content not found"));
        if (content.getStatus() == ContentStatus.PUBLISHED) { return; }
        if (content.getStatus() == ContentStatus.DRAFT) { content.changeStatus(ContentStatus.REVIEW); }
        content.changeStatus(ContentStatus.PUBLISHED);
        contents.save(content);
        guard.recordSuccess("LESSON_CONTENT_ACCESS", "Lesson", lessonId, "PUBLISH_LESSON_CONTENT");
    }

    public void requirements(UUID lessonId, boolean required) {
        var component = components.findByLessonId(lessonId).orElseThrow(() -> new NotFoundException("Lesson not found"));
        guard.requireCurator("LESSON_CONTENT_ACCESS", "Component", component.getId(), component.getOwnerId(), "SET_REQUIREMENTS");
        if (component.getStatus() != PublicationStatus.DRAFT) {
            throw new IllegalStateException("Requirements can only be edited before component publication");
        }
        jdbc.update("UPDATE content.lessons SET required_assignment=?,assignment_revision=assignment_revision+1 WHERE id=?", required, lessonId);
    }

    private void requireEnrollment(UUID courseId, boolean lock) {
        requireEnrollment(courseId, currentUser.requireCurrentUserId(), lock);
    }

    void requireEnrollment(UUID courseId, UUID userId, boolean lock) {
        List<UUID> enrollment = jdbc.queryForList("SELECT user_id FROM content.learning_enrollments "
                + "WHERE user_id=? AND course_id=?" + (lock ? " FOR UPDATE" : ""), UUID.class, userId, courseId);
        if (enrollment.isEmpty()) { throw new AccessDeniedException("Course is not assigned"); }
    }

    private LessonNode requireLesson(CourseView course, UUID lessonId) {
        return course.modules().stream().flatMap(module -> module.lessons().stream())
                .filter(lesson -> lesson.id().equals(lessonId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Lesson not found in course"));
    }

    private void requireAvailable(LessonNode lesson) {
        if (!lesson.available()) { throw new IllegalStateException("Complete the previous lesson first"); }
    }

    private static final class CourseProgress {
        private final UUID id;
        private final String title;
        private final String description;
        private final boolean started;
        private final java.util.Map<UUID,java.util.ArrayList<LessonNode>> modules = new java.util.LinkedHashMap<>();
        private final java.util.Map<UUID,String> titles = new java.util.HashMap<>();
        private boolean previousDone = true;
        private UUID current;
        private int total;
        private int completed;
        CourseProgress(UUID id,String title,String description,boolean started) {
            this.id=id;this.title=title;this.description=description;this.started=started;
        }
        void add(java.sql.ResultSet row) throws java.sql.SQLException {
            UUID lesson=row.getObject("lesson_id",UUID.class);
            if (lesson == null) { return; }
            if (!"PUBLISHED".equals(row.getString("module_status"))) { throw new IllegalStateException("Course contains an unpublished component"); }
            UUID module=row.getObject("module_id",UUID.class);
            boolean done=row.getObject("completed_lesson") != null;
            if (!done && current == null) { current=lesson; }
            modules.computeIfAbsent(module,key -> new java.util.ArrayList<>()).add(new LessonNode(lesson,
                    row.getString("lesson_title"),row.getInt("position"),row.getBoolean("required_assignment"),done,previousDone));
            titles.put(module,row.getString("module_title"));
            previousDone=previousDone && done;
            total++;if(done) { completed++; }
        }
        CourseView view() {
            String status=total>0 && completed==total ? "complete" : started ? "active" : "new";
            return new CourseView(id,title,description,status,total,completed,current,
                    modules.entrySet().stream().map(entry -> new ModuleNode(entry.getKey(),titles.get(entry.getKey()),List.copyOf(entry.getValue()))).toList());
        }
    }

    private record LessonRow(UUID id, String title, int position, boolean assignment, UUID moduleId,
                             String moduleTitle, String moduleStatus) { }
}
