package com.adventacademy.contentservice.adapter.rest.controller;

import com.adventacademy.contentservice.AbstractPostgresIT;
import com.adventacademy.contentservice.domain.ContentStatus;
import com.adventacademy.contentservice.domain.LessonContent;
import com.adventacademy.contentservice.domain.repository.LessonContentRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

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
class ConstructorApiIT extends AbstractPostgresIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LessonContentRepository lessonContentRepository;

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
}
