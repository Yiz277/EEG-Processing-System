package pitt.edu.publicGenerationSystem.service;

import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import pitt.edu.publicGenerationSystem.DTO.FileMeta;
import pitt.edu.publicGenerationSystem.DTO.FileReadyDto;
import pitt.edu.publicGenerationSystem.entity.Task;
import pitt.edu.publicGenerationSystem.entity.TaskFile;
import pitt.edu.publicGenerationSystem.middleware.JwtUtil;
import pitt.edu.publicGenerationSystem.repository.TaskFileRepository;
import pitt.edu.publicGenerationSystem.response.ApiResponse;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TaskFileService {
    @Autowired
    private TaskFileRepository taskFileRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public TaskFile findById(Long taskFileId) {
        TaskFile taskFile = taskFileRepository.findById(taskFileId).orElse(null);
        return taskFile;
    }

    public void copyFolderWithCleanTarget(Path source, Path target) throws IOException {
        // 如果目标文件夹已存在，先删除它
        if (Files.exists(target)) {
            deleteDirectory(target);
        }

        // 开始复制
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = target.resolve(source.relativize(file));
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // 递归删除文件夹
    public void deleteDirectory(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public int getNumOfCompletedTasksByWarehouseId(Long  warehouseId) {
        return taskFileRepository.getNumOfCompletedTasksByWarehouseId(warehouseId);
    }

    public int getNumOfTasksByWarehouseId(Long  warehouseId) {
        return taskFileRepository.getNumOfTasksByWarehouseId(warehouseId);
    }

    List<TaskFile> getTaskFileUnfinished() {
        return taskFileRepository.getTaskFileUnfinished();
    }

    List<TaskFile> getTaskFileByWarehouseId(Long  warehouseId) {
        return taskFileRepository.getTaskFileByWarehouseId(warehouseId);
    }

    List<TaskFile> getTaskFileByWarehouseIdAndUnfinished(Long  warehouseId) {
        return taskFileRepository.getTaskFileByWarehouseIdAndUnfinished(warehouseId);
    }
}
