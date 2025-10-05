package pitt.edu.publicGenerationSystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pitt.edu.publicGenerationSystem.entity.TaskFile;

@Repository
public interface TaskFileRepository  extends JpaRepository<TaskFile, Long> {
}
