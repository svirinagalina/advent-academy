package ru.adventacademy.myappbackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.adventacademy.myappbackend.entity.Task;
import ru.adventacademy.myappbackend.repository.TaskRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;

    // Получить все задачи
    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }

    // Получить задачу по ID
    public Optional<Task> getTaskById(Long id) {
        return taskRepository.findById(id);
    }

    // Получить задачи по сложности (Easy, Medium)
    public List<Task> getTasksByDifficulty(String difficulty) {
        return taskRepository.findByDifficulty(difficulty);
    }

    // Создать новую задачу (для админа)
    public Task createTask(Task task) {
        return taskRepository.save(task);
    }

    // Удалить задачу (для админа)
    public void deleteTask(Long id) {
        taskRepository.deleteById(id);
    }
}