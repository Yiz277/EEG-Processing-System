package pitt.edu.publicGenerationSystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pitt.edu.publicGenerationSystem.entity.Task;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    Optional<Task> findByName(String name);
    List<Task> findByStatus(Task.TaskStatus status);
    List<Task> findByTaskNameAndStatusIn(Task.TaskName taskName, List<Task.TaskStatus> statuses);
    List<Task> findByTaskNameAndStatus(Task.TaskName taskName, Task.TaskStatus status);
}