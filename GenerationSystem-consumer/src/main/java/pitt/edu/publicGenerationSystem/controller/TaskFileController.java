package pitt.edu.publicGenerationSystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pitt.edu.publicGenerationSystem.service.TaskFileService;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "http://localhost:8080", allowedHeaders = "*")
public class TaskFileController {
    @Autowired
    private TaskFileService taskFileService;

    @PostMapping("/get-file-by-range")
    public ResponseEntity<byte[]> getFileByIdAndRange(@RequestHeader("Authorization") String authHeader, @RequestBody Map<String, String> requestBody) {
        Long taskFileId = Long.valueOf(requestBody.get("task_file_id"));
        String sourcePath = requestBody.get("source_path");
        String fileName = requestBody.get("file_name");
        Long start = Long.valueOf(requestBody.get("start"));
        Long length = Long.valueOf(requestBody.get("length"));

        SecretKeySpec secretKey;
        try {
            String password = "090100";
            byte[] salt = "Wo you yi yu zheng".getBytes(StandardCharsets.UTF_8);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 256);
            SecretKey tmp = factory.generateSecret(spec);
            secretKey = new SecretKeySpec(tmp.getEncoded(), "AES");
        } catch (Exception e) {
            throw new RuntimeException("❌ 初始化 AES 密钥失败", e);
        }

        return taskFileService.getFileByIdAndRange(authHeader, taskFileId, sourcePath, fileName, start, length, secretKey);
    }

    @PostMapping("/delete-file-by-source-path")
    public ResponseEntity<Void> deleteFileBySourcePath(@RequestHeader("Authorization") String authHeader, @RequestBody Map<String, String> requestBody) {
        String sourcePath = requestBody.get("source_path");
        Long taskFileId = Long.valueOf(requestBody.get("task_file_id"));
        return taskFileService.deleteFileBySourcePath(authHeader, taskFileId, sourcePath);
    }
}
