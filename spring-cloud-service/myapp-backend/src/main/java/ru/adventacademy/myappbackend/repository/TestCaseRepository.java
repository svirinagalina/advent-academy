package ru.adventacademy.myappbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.adventacademy.myappbackend.entity.Task;
import ru.adventacademy.myappbackend.entity.TestCase;

import java.util.List;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    List<TestCase> findByTask(Task task);

    List<TestCase> findByTaskId(Long taskId);
}
