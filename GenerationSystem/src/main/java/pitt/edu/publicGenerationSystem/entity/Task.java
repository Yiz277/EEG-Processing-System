package pitt.edu.publicGenerationSystem.entity;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "tasks")
@EntityListeners(AuditingEntityListener.class)
public class Task implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_name", nullable = false)
    private TaskName taskName;

    @Column(name = "consumer_id", nullable = true)
    private Long consumerId;

    @Column(name = "source_path", nullable = false)
    private String sourcePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TaskStatus status = TaskStatus.PENDING;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Archive (Anonymization)
    // NoArEDF
    // Process
    // ArEDF
    // NoArCSV
    // ArCSV
    // Compression

    public enum TaskName {
        Archive, NoArEDF, Process, ArEDF, NoArCSV, ArCSV, Compression, Anonymization
    }

    // PENDING, 等待
    // LOADED, 送入队列
    // IN_PROGRESS, 正在处理
    // COMPLETED, 处理成功
    // FAILED, 处理失败

    public enum TaskStatus {
        PENDING, LOADED, IN_PROGRESS, COMPLETED, FAILED
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public Long getFileId() {
        return fileId;
    }
    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public TaskName getTaskName() {
        return taskName;
    }
    public void setTaskName(TaskName taskName) {
        this.taskName = taskName;
    }
    public Long getConsumerId() {
        return consumerId;
    }
    public void setConsumerId(Long consumerId) {
        this.consumerId = consumerId;
    }
    public String getSourcePath() {
        return sourcePath;
    }
    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }
    public TaskStatus getStatus() {
        return status;
    }
    public void setStatus(TaskStatus status) {
        this.status = status;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
