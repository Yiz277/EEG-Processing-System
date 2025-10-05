package pitt.edu.publicGenerationSystem.service;

import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import pitt.edu.publicGenerationSystem.process.ProcessManager;
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

    @Value("${ip.computerA}")
    private String computerAIp;

    @Transactional
    public TaskFile createTaskFile(Long originalFileIdLong, Long warehouseId, String name, TaskFile.FileStatus status) {
        TaskFile taskFile = new TaskFile();
        taskFile.setOriginalFileId(originalFileIdLong);
        taskFile.setWarehouseId(warehouseId);
        taskFile.setName(name);
        taskFile.setStatus(status);
        return taskFileRepository.save(taskFile);
    }

    public TaskFile findById(Long id) {
        return taskFileRepository.findById(id).orElse(null);
    }


    public List<Map<String, Object>> getAllFilesAtArchiveById(String authHeader, Long fileId) {
        String url = "http://" + computerAIp + ":8080/api/v1/get-all-files-at-archive";

        // 请求体
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("file_id", fileId.toString());

        // 请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", authHeader);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

        RestTemplate restTemplate = new RestTemplate();
        // 使用 postForObject（无法用泛型，只能接收为原始 ApiResponse<?>）
        ApiResponse<?> response = restTemplate.postForObject(url, entity, ApiResponse.class);

        if (response != null && "success".equals(response.getStatus())) {
            Object data = response.getData();
            List<Map<String, Object>> fileList = new ArrayList<>();
            if (data instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String filename = String.valueOf(entry.getKey());
                    Object size = entry.getValue();
                    Map<String, Object> f = new HashMap<>();
                    f.put("name", filename);
                    f.put("size", size);
                    fileList.add(f);
                }
                return fileList;
            }
        }
        return null;
    }

    public boolean addTask(String sourcePath, String redisToken, Task task, List<Map<String, Object>> fileList)
    {
        String base = "http://127.0.0.1:25812/task/add";
        Map<String, Object> body = new HashMap<>();
        body.put("sourcePath", sourcePath);     // 例如 "f125e63c-16a6-4338-85d8..."
        body.put("token", redisToken);                    // 不要带 "Bearer " 前缀
        body.put("warehouseId", findById(task.getFileId()).getWarehouseId().toString());
        List<Map<String, Object>> files = new ArrayList<>();
        for (Map<String, Object> fileInfo : fileList) {
            Map<String, Object> f = new HashMap<>();
            f.put("name", fileInfo.get("name"));
            f.put("size", fileInfo.get("size"));  // 若有提供 size，就传上
            files.add(f);
        }
        body.put("files", files);

        // 构造请求头（可选）
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 构造请求实体
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(base, entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                System.out.println("✅ 成功通知 Dokan 插入目录：" + sourcePath);
                return true;
            } else {
                System.err.println("❌ Dokan 返回失败状态: " + response.getStatusCode());

                // 失败 写入 task
                return false;
            }
        } catch (Exception e) {
            System.err.println("❌ 通知 Dokan 失败: " + e.getMessage());
            // 失败 写入 task
            return false;
        }
    }
    public boolean removeTask(String sourcePath) {
        String Base = "http://127.0.0.1:25812/task/remove";
        Map<String, Object> Body = new HashMap<>();
        Body.put("sourcePath", sourcePath);
        // 构造请求实体
        // 构造请求头（可选）
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> Entity = new HttpEntity<>(Body, headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.postForEntity(Base, Entity, String.class);
        if (response.getStatusCode() == HttpStatus.OK) {
            System.out.println("✅ 成功通知 Dokan 删除目录：" + sourcePath);
            return true;
        }
        return false;
    }

    public List<FileMeta> scanOutputFiles(Long taskFileId, String sourcePath) {
        List<FileMeta> files = new ArrayList<>();

        Path dir = Paths.get("C:/ProgramData/OutputMemfs//" + taskFileId, sourcePath);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    FileMeta f = new FileMeta();
                    f.setName(path.getFileName().toString());
                    f.setSize(Files.size(path));  // 注意：这里是原始大小
                    files.add(f);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("❌ 扫描文件失败: " + dir, e);
        }
        return files;
    }

    public ResponseEntity<byte[]> getFileByIdAndRange(String authHeader, Long taskFileId, String sourcePath, String fileName, Long start, Long length, SecretKeySpec secretKey) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        String token = authHeader.substring(7);
        Claims claims = JwtUtil.validateToken(token);
        if (claims == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        String redisToken = redisTemplate.opsForValue().get("AToken:");
        if (!token.equals(redisToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        try {
            // 拼接路径并校验防止路径穿越攻击
            Path basePath = Paths.get("C:\\ProgramData\\OutputMemfs\\" + taskFileId).toAbsolutePath().normalize();
            Path filePath = basePath.resolve(sourcePath).resolve(fileName).normalize();

            if (!filePath.startsWith(basePath)) {
                throw new SecurityException("❌ 非法路径访问！");
            }

            // 读取文件指定区域
            try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r")) {
                long fileLength = raf.length();
                if (start < 0 || start >= fileLength) {
                    return ResponseEntity.badRequest().body(null);
                }

                long safeLength = Math.min(length, fileLength - start);
                byte[] buffer = new byte[(int) safeLength];
                raf.seek(start);
                raf.readFully(buffer);

                // 加密内容（AES-GCM）
                byte[] iv = new byte[12]; // GCM 推荐 12字节 IV
                new SecureRandom().nextBytes(iv); // 生成随机 IV

                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv); // 128-bit authentication tag
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);
                byte[] encrypted = cipher.doFinal(buffer);

                // 拼接 IV + 密文
                byte[] output = new byte[iv.length + encrypted.length];
                System.arraycopy(iv, 0, output, 0, iv.length);
                System.arraycopy(encrypted, 0, output, iv.length, encrypted.length);

                // 构造响应
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                headers.setContentLength(output.length);

                return new ResponseEntity<>(output, headers, HttpStatus.OK);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    public static void deleteFolderAsUser(String folderPath) throws IOException {
        String username = "DESKTOP-KHQCQD7\\99415";
        String password = "090100";

        String escapedPath = folderPath.replace("\\", "\\\\");

        String psScript = String.join(" ", new String[]{
                "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8;",
                "$pass = ConvertTo-SecureString '" + password + "' -AsPlainText -Force;",
                "$cred = New-Object System.Management.Automation.PSCredential('" + username + "', $pass);",
                "$alist = @('-NoProfile','-Command','Remove-Item -Recurse -Force \"" + escapedPath + "\"');",
                "Start-Process -FilePath 'powershell.exe' -ArgumentList $alist -Credential $cred -WindowStyle Hidden -Wait"
        });

        String[] command = {"powershell.exe", "-NoProfile", "-Command", psScript};
        ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
        Process proc = pb.start();

        try {
            int exit = proc.waitFor();
            if (exit != 0) {
                System.err.println("⚠️ PowerShell 外层进程退出码: " + exit);
                System.err.println("⚠️ 虽然发生错误，但文件夹可能已成功删除");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("⚠️ 删除操作被中断（InterruptedException）");
        }
    }

    public ResponseEntity<Void> deleteFileBySourcePath(String authHeader, Long taskFileId, String sourcePath) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = authHeader.substring(7);
        Claims claims = JwtUtil.validateToken(token);
        if (claims == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String redisToken = redisTemplate.opsForValue().get("AToken:");
        if (!token.equals(redisToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 拼接完整路径
        Path basePath = Paths.get("C:\\ProgramData\\OutputMemfs\\" + taskFileId).toAbsolutePath().normalize();
        Path folderPath = basePath.resolve(sourcePath).normalize();

        // 防止路径穿越攻击
        if (!folderPath.startsWith(basePath)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (!Files.exists(folderPath)) {
            System.out.println("❌ 路径不存在：" + folderPath);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        if (!Files.isDirectory(folderPath)) {
            System.out.println("❌ 路径不是目录：" + folderPath);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        System.out.println("📁 路径存在：" + folderPath);
        try {
            // 先杀掉进程
            ProcessManager.killProcess(String.valueOf(taskFileId));

            // 递归删除整个目录
            deleteFolderAsUser(basePath.toString());

            System.out.println("🗑️ 已删除目录：" + basePath);

        } catch (IOException e) {
            e.printStackTrace();
        }

        return ResponseEntity.ok().build(); // 如果暂时不删，返回 200
    }
}
