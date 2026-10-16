package com.adventacademy.contentservice.application.service;

import com.adventacademy.contentservice.AbstractPostgresIT;
import com.adventacademy.contentservice.support.WithContentEditor;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.assertThat;

@WithContentEditor
class PublicationRaceIT extends AbstractPostgresIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired CourseAssemblyService courses;
    @Autowired ComponentLifecycleService components;

    @Test
    void publicationAndArchiveCannotCommitAnInvalidCourse() throws Exception {
        var security = SecurityContextHolder.getContext();
        try (var pool = Executors.newFixedThreadPool(2)) {
            for (int iteration = 0; iteration < 10; iteration++) {
                UUID module = UUID.randomUUID(), course = UUID.randomUUID();
                UUID owner = UUID.fromString(security.getAuthentication().getName());
                jdbc.update("INSERT INTO content.components(id,title,status,owner_id,created_at,updated_at,version) "
                        + "VALUES(?,'Race module','PUBLISHED',?,now(),now(),0)", module, owner);
                jdbc.update("INSERT INTO content.courses(id,title,status,owner_id,created_at,updated_at,version) "
                        + "VALUES(?,'Race course','DRAFT',?,now(),now(),0)", course, owner);
                jdbc.update("INSERT INTO content.course_components(course_id,component_id,position) VALUES(?,?,0)", course, module);
                var start = new CountDownLatch(1);
                var publish = pool.submit(() -> {
                    SecurityContextHolder.setContext(security);
                    try {
                        start.await();
                        try { courses.publish(course); } catch (com.adventacademy.contentservice.domain.constructor.CourseNotReadyForPublicationException expected) { }
                    } finally { SecurityContextHolder.clearContext(); }
                    return null;
                });
                var archive = pool.submit(() -> {
                    SecurityContextHolder.setContext(security);
                    try {
                        start.await();
                        try { components.archive(module); } catch (ComponentInUseException expected) { }
                    } finally { SecurityContextHolder.clearContext(); }
                    return null;
                });
                start.countDown(); publish.get(10, TimeUnit.SECONDS); archive.get(10, TimeUnit.SECONDS);
                boolean invalid = Boolean.TRUE.equals(jdbc.queryForObject("SELECT c.status='PUBLISHED' AND m.status<>'PUBLISHED' "
                        + "FROM content.courses c,content.components m WHERE c.id=? AND m.id=?", Boolean.class, course, module));
                assertThat(invalid).isFalse();
            }
        }
    }
}
