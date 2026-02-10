package ru.adventacademy.myappbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.adventacademy.myappbackend.entity.Task;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    // Найти все задачи по сложности
    List<Task> findByDifficulty(String difficulty);
}