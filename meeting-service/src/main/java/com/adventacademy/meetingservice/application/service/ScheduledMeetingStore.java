package com.adventacademy.meetingservice.application.service;

import com.adventacademy.meetingservice.domain.exception.ScheduleException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class ScheduledMeetingStore {
    private final JdbcTemplate jdbc;
    public ScheduledMeetingStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
    public record Entry(UUID id, UUID courseId, String courseTitle, UUID hostId, UUID requestId, String creationHash,
                        String title, Instant startsAt, Instant endsAt, String state, long version) { }
    public Entry get(UUID id, String lock) {
        return jdbc.query("SELECT * FROM meeting.scheduled_meetings WHERE id=?" + lock, this::entry, id).stream()
                .findFirst().orElseThrow(() -> new ScheduleException("MEETING_NOT_FOUND", "Scheduled meeting not found"));
    }
    public List<Entry> byRequest(UUID hostId, UUID requestId) {
        return jdbc.query("SELECT * FROM meeting.scheduled_meetings WHERE host_id=? AND request_id=?", this::entry, hostId, requestId);
    }
    public void insert(Entry entry, Instant createdAt) {
        jdbc.update("INSERT INTO meeting.scheduled_meetings(id,course_id,course_title,host_id,request_id,creation_hash,title,"
                        + "starts_at,ends_at,state,version,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?) "
                        + "ON CONFLICT (host_id,request_id) DO NOTHING",
                entry.id(), entry.courseId(), entry.courseTitle(), entry.hostId(), entry.requestId(), entry.creationHash(),
                entry.title(), Timestamp.from(entry.startsAt()), Timestamp.from(entry.endsAt()), entry.state(), 0, Timestamp.from(createdAt));
    }
    public List<UUID> participants(UUID id) {
        return jdbc.queryForList("SELECT user_id FROM meeting.scheduled_participants WHERE meeting_id=? ORDER BY user_id", UUID.class, id);
    }
    public void participants(UUID id, List<UUID> participants) {
        jdbc.update("DELETE FROM meeting.scheduled_participants WHERE meeting_id=?", id);
        for (UUID participant : participants) {
            jdbc.update("INSERT INTO meeting.scheduled_participants(meeting_id,user_id) VALUES (?,?)", id, participant);
        }
    }
    public void reschedule(Entry old, String title, Instant start, Instant end) {
        jdbc.update("UPDATE meeting.scheduled_meetings SET title=?,starts_at=?,ends_at=?,version=version+1 WHERE id=?",
                title, Timestamp.from(start), Timestamp.from(end), old.id());
    }
    public void transition(UUID id, String state) {
        jdbc.update("UPDATE meeting.scheduled_meetings SET state=?,version=version+1 WHERE id=?", state, id);
    }
    public List<Entry> visible(UUID actor, List<UUID> courses, boolean teacher, String tab, Instant now, int page, int size) {
        if (courses.isEmpty()) {
            return List.of();
        }
        final Visibility query = visibility(actor, courses, teacher, tab, now);
        final java.util.ArrayList<Object> args = new java.util.ArrayList<>(query.args());
        args.add(size);
        args.add((long) page * size);
        return jdbc.query("SELECT m.* FROM meeting.scheduled_meetings m WHERE " + query.filter()
                + " ORDER BY m.starts_at,m.id LIMIT ? OFFSET ?", this::entry, args.toArray());
    }
    public long visibleCount(UUID actor, List<UUID> courses, boolean teacher, String tab, Instant now) {
        if (courses.isEmpty()) {
            return 0;
        }
        final Visibility query = visibility(actor, courses, teacher, tab, now);
        final Long count = jdbc.queryForObject("SELECT count(*) FROM meeting.scheduled_meetings m WHERE " + query.filter(),
                Long.class, query.args().toArray());
        return java.util.Objects.requireNonNull(count);
    }
    private Visibility visibility(UUID actor, List<UUID> courses, boolean teacher, String tab, Instant now) {
        final String ids = String.join(",", java.util.Collections.nCopies(courses.size(), "?"));
        final java.util.ArrayList<Object> args = new java.util.ArrayList<>(courses);
        String filter = "m.course_id IN (" + ids + ")";
        if (!teacher) {
            filter += " AND EXISTS(SELECT 1 FROM meeting.scheduled_participants p WHERE p.meeting_id=m.id AND p.user_id=?)";
            args.add(actor);
        }
        if ("past".equals(tab)) {
            filter += " AND NOT (m.state IN ('PLANNED','ACTIVE') AND m.ends_at>?)";
        } else {
            filter += " AND m.state IN ('PLANNED','ACTIVE') AND m.ends_at>?";
        }
        args.add(Timestamp.from(now));
        return new Visibility(filter, args);
    }
    private record Visibility(String filter, List<Object> args) { }
    public List<UUID> elapsed(Instant now) {
        return jdbc.queryForList("SELECT id FROM meeting.scheduled_meetings WHERE ends_at<=? AND state IN ('PLANNED','ACTIVE') "
                + "ORDER BY ends_at LIMIT 100", UUID.class, Timestamp.from(now));
    }
    private Entry entry(ResultSet rs, int row) throws SQLException {
        return new Entry(rs.getObject("id", UUID.class), rs.getObject("course_id", UUID.class), rs.getString("course_title"),
                rs.getObject("host_id", UUID.class), rs.getObject("request_id", UUID.class), rs.getString("creation_hash"),
                rs.getString("title"), rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("ends_at").toInstant(),
                rs.getString("state"), rs.getLong("version"));
    }
}
