package ru.adventacademy.myappbackend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.adventacademy.myappbackend.entity.Submission;
import ru.adventacademy.myappbackend.entity.User;

import java.util.List;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    List<Submission> findByUser(User user);

    List<Submission> findByUserId(Long userId);

    List<Submission> findByTaskId(Long taskId);

    Page<Submission> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Submission> findByTaskIdOrderByCreatedAtDesc(Long taskId, Pageable pageable);

    Page<Submission> findByUserIdAndTaskIdOrderByCreatedAtDesc(Long userId, Long taskId, Pageable pageable);

    long countByUserIdAndTaskIdAndStatus(Long userId, Long taskId, Submission.Status status);
}
