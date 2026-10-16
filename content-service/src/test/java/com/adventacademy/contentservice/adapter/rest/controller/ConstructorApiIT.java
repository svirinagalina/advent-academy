package com.adventacademy.contentservice.adapter.rest.controller;

import com.adventacademy.contentservice.AbstractPostgresIT;
import com.adventacademy.contentservice.domain.ContentStatus;
import com.adventacademy.contentservice.domain.LessonContent;
import com.adventacademy.contentservice.domain.repository.LessonContentRepository;
import com.adventacademy.contentservice.support.WithContentEditor;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end scenario of the course constructor, driven through the REST API
 * exactly like a frontend would do it.
 */
@AutoConfigureMockMvc
@WithContentEditor
class ConstructorApiIT extends AbstractPostgresIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LessonContentRepository lessonContentRepository;

    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void cloneRetainsMandatoryAssignmentWithoutLearnerHistory() throws Exception {
        String component = mockMvc.perform(post("/api/components").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Assignment module\"}")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(component, "$.id");
        String lesson = mockMvc.perform(post("/api/components/{id}/lessons", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Required lesson\"}")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID source = UUID.fromString(JsonPath.read(lesson, "$.lessons[0].id"));
        jdbc.update("UPDATE content.lessons SET required_assignment=true WHERE id=?", source);
        jdbc.update("INSERT INTO content.lesson_assignments(lesson_id,title,description) VALUES(?,'Task','Review required')", source);
        String cloned = mockMvc.perform(post("/api/components/{id}/clone", id)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID target = UUID.fromString(JsonPath.read(cloned, "$.lessons[0].id"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/lessons/{id}/assignment/configuration", target))
                .andExpect(status().isOk()).andExpect(jsonPath("$.required").value(true))
                .andExpect(jsonPath("$.title").value("Task")).andExpect(jsonPath("$.description").value("Review required"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM content.assignment_submissions WHERE lesson_id=?", Integer.class, target)).isZero();
    }

    @Test
    void fullHappyPathFromComponentToDirection() throws Exception {
        // 1. создаём компонент
        String component = mockMvc.perform(post("/api/components")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Collections\", \"description\": \"About collections\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        String componentId = JsonPath.read(component, "$.id");

        // 2. добавляем урок
        String withLesson = mockMvc.perform(post("/api/components/{id}/lessons", componentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Lists\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lessons", hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        UUID lessonId = UUID.fromString(JsonPath.read(withLesson, "$.lessons[0].id"));

        // 3. публикация компонента без готового контента -> 422 со списком виновников
        mockMvc.perform(post("/api/components/{id}/publish", componentId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.notReadyIds[0]").value(lessonId.toString()));

        // 4. доводим контент урока до PUBLISHED (эндпоинта смены статуса пока нет — через репозиторий)
        LessonContent content = LessonContent.create(lessonId, "# Lists");
        content.changeStatus(ContentStatus.REVIEW);
        content.changeStatus(ContentStatus.PUBLISHED);
        lessonContentRepository.save(content);

        // 5. теперь компонент публикуется
        mockMvc.perform(post("/api/components/{id}/publish", componentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        // 6. создаём курс и кладём в него компонент
        String course = mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Java Basics\", \"description\": \"About java\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String courseId = JsonPath.read(course, "$.id");

        mockMvc.perform(post("/api/courses/{id}/components", courseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"componentId\": \"" + componentId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.components[0].componentId").value(componentId));

        // 7. публикуем курс (компонент уже PUBLISHED — можно)
        mockMvc.perform(post("/api/courses/{id}/publish", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mockMvc.perform(post("/api/components/{id}/archive", componentId))
                .andExpect(status().isConflict());

        // 8. создаём направление и кладём в него курс
        String direction = mockMvc.perform(post("/api/directions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Java Backend\", \"description\": \"Path\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String directionId = JsonPath.read(direction, "$.id");

        mockMvc.perform(post("/api/directions/{id}/courses", directionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\": \"" + courseId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courses[0].courseId").value(courseId));

        // 9. дерево курса: курс -> компонент -> урок
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/courses/{id}", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components[0].title").value("Collections"))
                .andExpect(jsonPath("$.components[0].lessons[0].title").value("Lists"));
    }

    @Test
    void publishingCourseWithDraftComponentReturns422() throws Exception {
        String component = mockMvc.perform(post("/api/components")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Draft component\", \"description\": null}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String componentId = JsonPath.read(component, "$.id");

        String course = mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Course\", \"description\": null}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String courseId = JsonPath.read(course, "$.id");

        mockMvc.perform(post("/api/courses/{id}/components", courseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"componentId\": \"" + componentId + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/courses/{id}/publish", courseId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.notReadyIds[0]").value(componentId));
    }

    @Test
    void deletingComponentUsedByCourseReturns409() throws Exception {
        String component = mockMvc.perform(post("/api/components")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Used component\", \"description\": null}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String componentId = JsonPath.read(component, "$.id");

        String course = mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Course\", \"description\": null}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String courseId = JsonPath.read(course, "$.id");

        mockMvc.perform(post("/api/courses/{id}/components", courseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"componentId\": \"" + componentId + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/components/{id}", componentId))
                .andExpect(status().isConflict());
    }

    @Test
    void addingUnknownComponentToCourseReturns400() throws Exception {
        String course = mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Course\", \"description\": null}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String courseId = JsonPath.read(course, "$.id");

        mockMvc.perform(post("/api/courses/{id}/components", courseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"componentId\": \"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test
    void reorderingLessonsWithForeignIdsReturns400() throws Exception {
        String component = mockMvc.perform(post("/api/components")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Collections\", \"description\": null}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String componentId = JsonPath.read(component, "$.id");

        mockMvc.perform(post("/api/components/{id}/lessons", componentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Lists\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/components/{id}/lessons", componentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Maps\"}"))
                .andExpect(status().isCreated());

        // чужие id, не принадлежащие компоненту
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/components/{id}/lessons/order", componentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderedIds\": [\"" + UUID.randomUUID() + "\", \"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reorderingLessonsWithDuplicateOrMissingIdsReturns400() throws Exception {
        String component = mockMvc.perform(post("/api/components")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Collections\", \"description\": null}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String componentId = JsonPath.read(component, "$.id");

        String withLesson = mockMvc.perform(post("/api/components/{id}/lessons", componentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Lists\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String lessonId = JsonPath.read(withLesson, "$.lessons[0].id");
        mockMvc.perform(post("/api/components/{id}/lessons", componentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Maps\"}"))
                .andExpect(status().isCreated());

        // дублирующийся id вместо второго урока
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/components/{id}/lessons/order", componentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderedIds\": [\"" + lessonId + "\", \"" + lessonId + "\"]}"))
                .andExpect(status().isBadRequest());

        // отсутствующий id (передан только один из двух уроков)
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/components/{id}/lessons/order", componentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderedIds\": [\"" + lessonId + "\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void modifyingPublishedComponentReturns409() throws Exception {
        String component = mockMvc.perform(post("/api/components")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Collections\", \"description\": null}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String componentId = JsonPath.read(component, "$.id");

        String withLesson = mockMvc.perform(post("/api/components/{id}/lessons", componentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Lists\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID lessonId = UUID.fromString(JsonPath.read(withLesson, "$.lessons[0].id"));

        LessonContent content = LessonContent.create(lessonId, "# Lists");
        content.changeStatus(ContentStatus.REVIEW);
        content.changeStatus(ContentStatus.PUBLISHED);
        lessonContentRepository.save(content);

        mockMvc.perform(post("/api/components/{id}/publish", componentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        // попытка добавить урок в уже опубликованный компонент
        mockMvc.perform(post("/api/components/{id}/lessons", componentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"New lesson\"}"))
                .andExpect(status().isConflict());

        // попытка переименовать уже опубликованный компонент
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/components/{id}", componentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Renamed\", \"description\": null}"))
                .andExpect(status().isConflict());
    }

    @Test
    void deletingCourseUsedByDirectionReturns409() throws Exception {
        String course = mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Used course\", \"description\": null}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String courseId = JsonPath.read(course, "$.id");

        String direction = mockMvc.perform(post("/api/directions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Direction\", \"description\": null}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String directionId = JsonPath.read(direction, "$.id");

        mockMvc.perform(post("/api/directions/{id}/courses", directionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\": \"" + courseId + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/courses/{id}", courseId))
                .andExpect(status().isConflict());
    }

    @Test
    void cloningComponentCreatesIndependentDraftCopy() throws Exception {
        String component = mockMvc.perform(post("/api/components")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Original\", \"description\": \"Desc\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String componentId = JsonPath.read(component, "$.id");

        mockMvc.perform(post("/api/components/{id}/lessons", componentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Lists\"}"))
                .andExpect(status().isCreated());

        String clone = mockMvc.perform(post("/api/components/{id}/clone", componentId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.title").value("Original"))
                .andExpect(jsonPath("$.lessons", hasSize(1)))
                .andExpect(jsonPath("$.lessons[0].title").value("Lists"))
                .andReturn().getResponse().getContentAsString();

        String cloneId = JsonPath.read(clone, "$.id");
        String cloneLessonId = JsonPath.read(clone, "$.lessons[0].id");
        assertThat(cloneId).isNotEqualTo(componentId);

        // клон независим: изменение оригинала не переиспользуется в клоне и наоборот
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/components/{id}/lessons/{lessonId}", cloneId, cloneLessonId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Renamed in clone\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/components/{id}", componentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lessons[0].title").value("Lists"));
    }

    @Test
    void promotingComponentCreatesDraftCourseWrappingIt() throws Exception {
        String component = mockMvc.perform(post("/api/components")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Collections\", \"description\": \"About collections\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String componentId = JsonPath.read(component, "$.id");

        mockMvc.perform(post("/api/components/{id}/promote", componentId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.title").value("Collections"))
                .andExpect(jsonPath("$.components", hasSize(1)))
                .andExpect(jsonPath("$.components[0].componentId").value(componentId));
    }
}