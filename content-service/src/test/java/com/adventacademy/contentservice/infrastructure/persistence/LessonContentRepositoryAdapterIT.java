package com.adventacademy.contentservice.infrastructure.persistence;

import com.adventacademy.contentservice.domain.LessonContent;
import com.adventacademy.contentservice.domain.repository.LessonContentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LessonContentRepositoryAdapterIT {

    @Autowired
    private LessonContentRepository repository;
    @PersistenceContext
    private EntityManager em;

    @Test
    void saveAndFindById() {
        LessonContent content = LessonContent.create(UUID.randomUUID(), "# Урок 1");

        repository.save(content);
        Optional<LessonContent> found = repository.findById(content.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getMarkdown()).isEqualTo("# Урок 1");
        assertThat(found.get().getLessonId()).isEqualTo(content.getLessonId());
    }

    @Test
    void saveAndFindByLessonId() {
        LessonContent content = LessonContent.create(UUID.randomUUID(), "# Урок 1");

        repository.save(content);
        Optional<LessonContent> found = repository.findByLessonId(content.getLessonId());

        assertThat(found).isPresent();
        assertThat(found.get().getMarkdown()).isEqualTo("# Урок 1");
        assertThat(found.get().getId()).isEqualTo(content.getId());
    }

    @Test
    void deleteById() {
        LessonContent content = LessonContent.create(UUID.randomUUID(), "# Урок 1");
        repository.save(content);

        repository.deleteById(content.getId());

        assertThat(repository.findById(content.getId())).isEmpty();
    }

    @Test
    void savingSecondContentForSameLessonFails() {
        UUID lessonId = UUID.randomUUID();
        repository.save(LessonContent.create(lessonId, "первый"));

        assertThatThrownBy(() -> {
            repository.save(LessonContent.create(lessonId, "второй"));

            em.flush();
        }).isInstanceOf(org.hibernate.exception.ConstraintViolationException.class);
    }

    @Test
    void updateContent() {
        LessonContent content = LessonContent.create(UUID.randomUUID(), "# Урок 1");
        repository.save(content);                                                  // сохранили старую версию

        content.updateContent("# Новый текст", "<h1>Новый текст</h1>", Clock.systemUTC()); // изменили объект
        repository.save(content);                                                  // сохранили новую версию

        Optional<LessonContent> found = repository.findById(content.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getMarkdown()).isEqualTo("# Новый текст");          // в базе — новый текст
    }
}