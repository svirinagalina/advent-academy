package ru.adventacademy.myappbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.adventacademy.myappbackend.dto.SubmissionResultDto;
import ru.adventacademy.myappbackend.entity.Submission;
import ru.adventacademy.myappbackend.entity.Task;
import ru.adventacademy.myappbackend.entity.User;
import ru.adventacademy.myappbackend.repository.SubmissionRepository;
import ru.adventacademy.myappbackend.repository.TaskRepository;
import ru.adventacademy.myappbackend.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @Transactional
    public Submission saveSubmission(Long userId, Long taskId, String sourceCode,
                                     Integer languageId, SubmissionResultDto result) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        Submission.Status status;
        if (result.isAllPassed()) {
            status = Submission.Status.OK;
        } else if (result.getPassedTests() > 0) {
            status = Submission.Status.FAIL;
        } else {
            status = Submission.Status.ERROR;
        }

        Submission submission = Submission.builder()
                .user(user)
                .task(task)
                .sourceCode(sourceCode)
                .languageId(languageId)
                .status(status)
                .message(result.getMessage())
                .passedTests(result.getPassedTests())
                .totalTests(result.getTotalTests())
                .executionDetails(result.getExecutionDetails())
                .createdAt(LocalDateTime.now())
                .build();

        log.info("Saving submission for user {} and task {}: status={}, passed={}/{}",
                userId, taskId, status, result.getPassedTests(), result.getTotalTests());

        return submissionRepository.save(submission);
    }

    public List<Submission> getUserSubmissions(Long userId) {
        return submissionRepository.findByUserId(userId);
    }

    public List<Submission> getTaskSubmissions(Long taskId) {
        return submissionRepository.findByTaskId(taskId);
    }

    public Page<Submission> getUserSubmissionsPaginated(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return submissionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public Page<Submission> getTaskSubmissionsPaginated(Long taskId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return submissionRepository.findByTaskIdOrderByCreatedAtDesc(taskId, pageable);
    }

    public Page<Submission> getUserTaskSubmissions(Long userId, Long taskId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return submissionRepository.findByUserIdAndTaskIdOrderByCreatedAtDesc(userId, taskId, pageable);
    }

    public long countSuccessfulSubmissions(Long userId, Long taskId) {
        return submissionRepository.countByUserIdAndTaskIdAndStatus(userId, taskId, Submission.Status.OK);
    }
}
