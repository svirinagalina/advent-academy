package ru.adventacademy.myappbackend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.adventacademy.myappbackend.dto.CodeSubmissionDto;
import ru.adventacademy.myappbackend.dto.SubmissionDto;
import ru.adventacademy.myappbackend.dto.SubmissionResultDto;
import ru.adventacademy.myappbackend.dto.TaskDto;
import ru.adventacademy.myappbackend.entity.Submission;
import ru.adventacademy.myappbackend.entity.Task;
import ru.adventacademy.myappbackend.entity.User;
import ru.adventacademy.myappbackend.repository.UserRepository;
import ru.adventacademy.myappbackend.service.CodeExecutionService;
import ru.adventacademy.myappbackend.service.SubmissionService;
import ru.adventacademy.myappbackend.service.TaskService;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final CodeExecutionService codeExecutionService;
    private final SubmissionService submissionService;
    private final UserRepository userRepository;

    // Получить все задачи (доступно всем)
    @GetMapping
    public ResponseEntity<List<TaskDto>> getAllTasks() {
        List<Task> tasks = taskService.getAllTasks();
        List<TaskDto> taskDtos = tasks.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(taskDtos);
    }

    // Получить задачу по ID (доступно всем)
    @GetMapping("/{id}")
    public ResponseEntity<TaskDto> getTaskById(@PathVariable Long id) {
        return taskService.getTaskById(id)
                .map(task -> ResponseEntity.ok(convertToDto(task)))
                .orElse(ResponseEntity.notFound().build());
    }

    // Получить задачи по сложности (доступно всем)
    @GetMapping("/difficulty/{difficulty}")
    public ResponseEntity<List<TaskDto>> getTasksByDifficulty(@PathVariable String difficulty) {
        List<Task> tasks = taskService.getTasksByDifficulty(difficulty);
        List<TaskDto> taskDtos = tasks.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(taskDtos);
    }

    // Создать задачу (только для ADMIN)
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TaskDto> createTask(@RequestBody TaskDto taskDto) {
        Task task = convertToEntity(taskDto);
        Task savedTask = taskService.createTask(task);
        return ResponseEntity.ok(convertToDto(savedTask));
    }

    // Удалить задачу (только для ADMIN)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.ok().build();
    }

    // Конвертация Entity -> DTO
    private TaskDto convertToDto(Task task) {
        return TaskDto.builder()
                .id(task.getId())
                .title(task.getTitle())
                .shortDescription(task.getShortDescription())
                .fullDescription(task.getFullDescription())
                .languageId(task.getLanguageId())
                .difficulty(task.getDifficulty())
                .build();
    }

    // Конвертация DTO -> Entity
    private Task convertToEntity(TaskDto dto) {
        return Task.builder()
                .id(dto.getId())
                .title(dto.getTitle())
                .shortDescription(dto.getShortDescription())
                .fullDescription(dto.getFullDescription())
                .languageId(dto.getLanguageId())
                .difficulty(dto.getDifficulty())
                .build();
    }
    // Проверить решение задачи
    @PostMapping("/{id}/submit")
    public ResponseEntity<SubmissionResultDto> submitSolution(
            @PathVariable Long id,
            @RequestBody CodeSubmissionDto submission,
            Authentication authentication) {

        // Выполняем код
        SubmissionResultDto result = codeExecutionService.executeCodeWithTests(
                id,
                submission.getSourceCode(),
                submission.getLanguageId()
        );

        // Сохраняем результат в БД если пользователь авторизован
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            submissionService.saveSubmission(
                    user.getId(),
                    id,
                    submission.getSourceCode(),
                    submission.getLanguageId(),
                    result
            );
        }

        return ResponseEntity.ok(result);
    }

    // Получить историю попыток пользователя для задачи
    @GetMapping("/{taskId}/submissions")
    public ResponseEntity<Page<SubmissionDto>> getTaskSubmissions(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }

        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Page<Submission> submissions = submissionService.getUserTaskSubmissions(
                user.getId(), taskId, page, size);

        Page<SubmissionDto> dtos = submissions.map(this::convertSubmissionToDto);

        return ResponseEntity.ok(dtos);
    }

    // Получить все попытки пользователя
    @GetMapping("/submissions/my")
    public ResponseEntity<Page<SubmissionDto>> getMySubmissions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }

        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Page<Submission> submissions = submissionService.getUserSubmissionsPaginated(
                user.getId(), page, size);

        Page<SubmissionDto> dtos = submissions.map(this::convertSubmissionToDto);

        return ResponseEntity.ok(dtos);
    }

    // Конвертация Submission -> DTO
    private SubmissionDto convertSubmissionToDto(Submission submission) {
        return SubmissionDto.builder()
                .id(submission.getId())
                .taskId(submission.getTask().getId())
                .taskTitle(submission.getTask().getTitle())
                .sourceCode(submission.getSourceCode())
                .languageId(submission.getLanguageId())
                .status(submission.getStatus().name())
                .message(submission.getMessage())
                .passedTests(submission.getPassedTests())
                .totalTests(submission.getTotalTests())
                .executionDetails(submission.getExecutionDetails())
                .createdAt(submission.getCreatedAt())
                .build();
    }

    // Получить статистику по задаче для пользователя
    @GetMapping("/{taskId}/stats")
    public ResponseEntity<TaskStatistics> getTaskStatistics(
            @PathVariable Long taskId,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }

        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        long successfulAttempts = submissionService.countSuccessfulSubmissions(user.getId(), taskId);
        Page<Submission> allAttempts = submissionService.getUserTaskSubmissions(
                user.getId(), taskId, 0, Integer.MAX_VALUE);

        TaskStatistics stats = new TaskStatistics(
                allAttempts.getTotalElements(),
                successfulAttempts,
                successfulAttempts > 0
        );

        return ResponseEntity.ok(stats);
    }

    // Внутренний класс для статистики
    public record TaskStatistics(long totalAttempts, long successfulAttempts, boolean solved) {
    }

}