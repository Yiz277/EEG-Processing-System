package pitt.edu.publicGenerationSystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pitt.edu.publicGenerationSystem.entity.TaskFile;
import pitt.edu.publicGenerationSystem.entity.Warehouse;

import java.util.List;
import org.springframework.data.repository.query.Param;

@Repository
public interface TaskFileRepository  extends JpaRepository<TaskFile, Long> {
    @Query(value = "SELECT COUNT(DISTINCT id) FROM task_files WHERE status IN ('COMPLETED', 'FAILED') AND warehouse_id = :warehouseId", nativeQuery = true)
    int getNumOfCompletedTasksByWarehouseId(@Param("warehouseId") Long warehouseId);

    @Query(value = "SELECT COUNT(*) FROM task_files WHERE warehouse_id = :warehouseId", nativeQuery = true)
    int getNumOfTasksByWarehouseId(@Param("warehouseId") Long warehouseId);

    @Query(value = "SELECT * FROM task_files WHERE status NOT IN ('COMPLETED', 'FAILED')", nativeQuery = true)
    List<TaskFile> getTaskFileUnfinished();

    @Query(value = "SELECT * FROM task_files WHERE status NOT IN ('COMPLETED', 'FAILED') AND warehouse_id = :warehouseId", nativeQuery = true)
    List<TaskFile> getTaskFileByWarehouseIdAndUnfinished(@Param("warehouseId") Long warehouseId);

    @Query(value = "SELECT * FROM task_files WHERE warehouse_id = :warehouseId", nativeQuery = true)
    List<TaskFile> getTaskFileByWarehouseId(@Param("warehouseId") Long warehouseId);
}
