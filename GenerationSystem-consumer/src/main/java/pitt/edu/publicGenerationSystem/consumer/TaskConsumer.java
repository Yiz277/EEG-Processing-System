package pitt.edu.publicGenerationSystem.consumer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.amqp.core.Message;
import pitt.edu.publicGenerationSystem.DTO.FileMeta;
import pitt.edu.publicGenerationSystem.entity.Task;
import pitt.edu.publicGenerationSystem.process.ProcessManager;
import pitt.edu.publicGenerationSystem.repository.TaskRepository;
import pitt.edu.publicGenerationSystem.service.TaskFileService;
import pitt.edu.publicGenerationSystem.service.TaskService;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class TaskConsumer {
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private final ExecutorService archiveExecutor = Executors.newFixedThreadPool(4);

    @Autowired
    private TaskFileService taskFileService;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private TaskService taskService;

    @Value("${ip.computerA}")
    private String computerAIp;
    @Value("${ip.computerS}")
    private String computerSIp;
    @Value("${ip.computer}")
    private String computerIp;

    public static int runCommand(List<String> cmd) {
        try {
            System.out.println("▶️ 正在执行命令:\n" + String.join(" ", cmd));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process process = pb.start();

            // 打印输出流和错误流
            try (BufferedReader stdOut = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader stdErr = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

                System.out.println("📤 STDOUT:");
                stdOut.lines().forEach(line -> System.out.println("  " + line));

                System.out.println("❗STDERR:");
                stdErr.lines().forEach(line -> System.err.println("  " + line));
            }

            int exitCode = process.waitFor();
            System.out.println("✅ 命令退出码: " + exitCode);
            return exitCode;

        } catch (Exception e) {
            System.err.println("❌ 执行命令失败: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }


    private boolean archive(Task task) {
        String redisToken = redisTemplate.opsForValue().get("token:");
        System.out.println(redisToken);
        String sourcePath = task.getSourcePath();  // 比如 task123 或者 patient001
        List<Map<String, Object>> fileList = taskFileService.getAllFilesAtArchiveById("Bearer " + redisToken, task.getFileId());
        System.out.println(fileList);

        // 内存系统增加这个文件夹和所有文件
        if (!taskFileService.addTask(sourcePath, redisToken, task, fileList)) {
            return false;
        }

        // 启动文件夹
        try {
            ProcessManager.startProcess("C:\\Users\\99415\\Desktop\\NetTransferBack\\bin\\Debug\\net8.0-windows\\NetTransferBack.exe", String.valueOf(task.getFileId()));
        } catch (IOException e) {
            System.err.println("❌ 启动进程失败: " + e.getMessage());
            e.printStackTrace(); // 打印完整堆栈信息（建议保留）
            return false;
        }

        // archive
        try {
            File folder = new File("C:\\ProgramData\\Memfs\\" + sourcePath);
            if (!folder.exists() || !folder.isDirectory()) {
                System.err.println("❌ 路径不存在或不是目录: " + folder.getAbsolutePath());
                return false;
            }
            File[] erdFiles = folder.listFiles(file ->
                    file.isFile() &&
                            file.getName().toLowerCase().endsWith(".erd") &&
                            file.getName().contains(sourcePath)
            );

            if (erdFiles == null || erdFiles.length == 0) {
                System.err.println("⚠️ 没有找到符合条件的 .erd 文件");
                return false;
            }

            // 找出文件名最短的那个
            File shortest = Arrays.stream(erdFiles)
                    .min(Comparator.comparingInt(f -> f.getName().length()))
                    .orElse(null);

            String shortestEedName = shortest.getName();
            List<String> cmd = List.of(
                    "C:\\Program Files (x86)\\Persyst\\Insight\\PSCLI.exe",
                    "/Archive",
                    "/Downsample=Off",
                    "/ArtifactReduction=Off",
                    "/SourceFile=\"" + "C:\\ProgramData\\Memfs\\" + sourcePath + "\\" + shortestEedName + "\"",
                    "/FileType=XLTEK",
                    "/OutputFile=\"" + "C:\\ProgramData\\OutputMemfs\\" + task.getFileId() + "\\" + sourcePath + "\\" + sourcePath + ".lay\""
            );

            int exitCode = runCommand(cmd);

            if (taskFileService.removeTask(sourcePath)) {}
            else {
                System.out.println("Dokan 删除目录失败: " + sourcePath);
            }
            if (exitCode != 0) {
                return false;
            }
            // 成功，通知一下A
            List<FileMeta> files = taskFileService.scanOutputFiles(task.getFileId(), sourcePath);
            boolean result = taskService.notifyAFinished(task.getId(), sourcePath, files, computerIp + ":8080", redisToken, "success");
            if (!result) { return false; }
        } catch (Exception e) {
            System.err.println("❌ 执行命令失败: " + e.getMessage());
            return false;
        }
        return true;
    }

    public boolean moveFromSmbShare(Task task) {
        List<Task.TaskName> datTaskName = Arrays.asList(Task.TaskName.NoArEDF, Task.TaskName.Process);
        String sourcePath = task.getSourcePath();

        Path sourceDir;
        if (datTaskName.contains(task.getTaskName())) {
            sourceDir = Paths.get("\\\\"+ computerSIp + "\\shared_space\\dat", String.valueOf(task.getId()), sourcePath);
        } else {
            sourceDir = Paths.get("\\\\" + computerSIp + "\\shared_space\\processed_dat", String.valueOf(task.getId()), sourcePath);
        }

        Path targetDir = Paths.get("D:\\swap_space", String.valueOf(task.getId()), sourcePath);

        try {
            // ✅ 如果目标目录存在，先递归删除
            if (Files.exists(targetDir)) {
                Files.walkFileTree(targetDir, new SimpleFileVisitor<Path>() {
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

            // 创建新的空目录
            Files.createDirectories(targetDir);

            Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path relative = sourceDir.relativize(dir);
                    Files.createDirectories(targetDir.resolve(relative));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path relative = sourceDir.relativize(file);
                    Files.copy(file, targetDir.resolve(relative), StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });

            // ✅ 拷贝完成，删除原始目录
            Files.walkFileTree(sourceDir.getParent(), new SimpleFileVisitor<Path>() {
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

            System.out.println("✅ 成功拷贝并删除: " + sourceDir + " → " + targetDir);
            return true;

        } catch (IOException e) {
            System.err.println("❌ 拷贝失败: " + e.getMessage());
            return false;
        }
    }

    public boolean cleaning(Task task, String fileName) {
        String sourcePath = task.getSourcePath();

        Path sourceDir;
        Path targetDir;

        if (task.getTaskName().equals(Task.TaskName.Process)) {
            // 处理整个文件夹
            sourceDir = Paths.get("D:\\swap_space", String.valueOf(task.getId()), sourcePath);
            targetDir = Paths.get("\\\\" + computerSIp + "\\shared_space\\processed_dat", sourcePath);

            try {
                // 删除原始目录
                Files.walkFileTree(Paths.get("D:\\swap_space", String.valueOf(task.getId())), new SimpleFileVisitor<Path>() {
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

                System.out.println("✅ 成功删除文件夹: " + sourceDir + " → " + targetDir);
                return true;

            } catch (IOException e) {
                System.err.println("❌ 删除失败: " + e.getMessage());
                return false;
            }

        } else {
            // fileName 是单个文件
            sourceDir = Paths.get("D:\\output_space", fileName);
            targetDir = Paths.get("\\\\" + computerSIp +"\\shared_space\\output", fileName);

            try {
                Files.delete(sourceDir); // 删除源文件
                System.out.println("✅ 成功删除文件: " + sourceDir + " → " + targetDir);
            } catch (IOException e) {
                System.err.println("❌ 文件拷贝失败: " + e.getMessage());
                return false;
            }


            try {
                // 删除原始目录
                Files.walkFileTree(Paths.get("D:\\swap_space", String.valueOf(task.getId())), new SimpleFileVisitor<Path>() {
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

                return true;

            } catch (IOException e) {
                System.err.println("❌ 文件删除失败: " + e.getMessage());
                return false;
            }
        }
    }


    public boolean moveToSmbShare(Task task, String fileName) {
        String sourcePath = task.getSourcePath();

        Path sourceDir;
        Path targetDir;

        if (task.getTaskName().equals(Task.TaskName.Process)) {
            // 处理整个文件夹
            sourceDir = Paths.get("D:\\swap_space", String.valueOf(task.getId()), sourcePath);
            targetDir = Paths.get("\\\\" + computerSIp + "\\shared_space\\processed_dat", sourcePath);

            try {
                Files.createDirectories(targetDir);

                Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        Path relative = sourceDir.relativize(dir);
                        Files.createDirectories(targetDir.resolve(relative));
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Path relative = sourceDir.relativize(file);
                        Files.copy(file, targetDir.resolve(relative), StandardCopyOption.REPLACE_EXISTING);
                        return FileVisitResult.CONTINUE;
                    }
                });

                // 删除原始目录
                Files.walkFileTree(Paths.get("D:\\swap_space", String.valueOf(task.getId())), new SimpleFileVisitor<Path>() {
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

                System.out.println("✅ 成功移动文件夹: " + sourceDir + " → " + targetDir);
                return true;

            } catch (IOException e) {
                System.err.println("❌ 拷贝失败: " + e.getMessage());
                return false;
            }

        } else {
            // fileName 是单个文件
            sourceDir = Paths.get("D:\\output_space", fileName);
            targetDir = Paths.get("\\\\" + computerSIp + "\\shared_space\\output", fileName);

            try {
                Files.createDirectories(targetDir.getParent()); // 确保父目录存在
                Files.copy(sourceDir, targetDir, StandardCopyOption.REPLACE_EXISTING);
                Files.delete(sourceDir); // 删除源文件

                System.out.println("✅ 成功移动文件: " + sourceDir + " → " + targetDir);

                // 删除原始目录
                Files.walkFileTree(Paths.get("D:\\swap_space", String.valueOf(task.getId())), new SimpleFileVisitor<Path>() {
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

                return true;

            } catch (IOException e) {
                System.err.println("❌ 文件拷贝失败: " + e.getMessage());
                return false;
            }
        }
    }

    private boolean noArEDF(Task task) {
        if (!moveFromSmbShare(task)) { return false; }

        String sourcePath = task.getSourcePath();
        Path targetDir = Paths.get("D:\\swap_space", String.valueOf(task.getId()), sourcePath);
        // 3. 查找 .lay 文件
        File[] layFiles = targetDir.toFile().listFiles(f ->
                f.isFile() && f.getName().toLowerCase().endsWith(".lay"));

        if (layFiles == null || layFiles.length == 0) {
            System.err.println("⚠️ 未找到 .lay 文件");
            return false;
        }

        // 打印找到的第一个 .lay 文件
        System.out.println("✅ 找到 .lay 文件: " + layFiles[0].getAbsolutePath());

        List<String> cmd = List.of(
                "C:\\Program Files (x86)\\Persyst\\Insight\\PSCLI.exe",
                "/Archive",
                "/Downsample=Off",
                "/ArtifactReduction=Off",
                "/SourceFile=\"" + layFiles[0].getAbsolutePath() + "\"",
                "/OutputFile=\"D:\\output_space\\" + sourcePath + "_noar.edf\""
        );
        int exitCode = runCommand(cmd);

        if (exitCode != 0) {
            return false;
        }

        // move to S output and delete
        boolean result = moveToSmbShare(task, sourcePath + "_noar.edf");
        if (!result) { return false; }

        return true;
    }

    private boolean process(Task task) {
        if (!moveFromSmbShare(task)) { return false; }

        String sourcePath = task.getSourcePath();
        Path targetDir = Paths.get("D:\\swap_space", String.valueOf(task.getId()), sourcePath);
        // 3. 查找 .lay 文件
        File[] layFiles = targetDir.toFile().listFiles(f ->
                f.isFile() && f.getName().toLowerCase().endsWith(".lay"));

        if (layFiles == null || layFiles.length == 0) {
            System.err.println("⚠️ 未找到 .lay 文件");
            return false;
        }

        // 打印找到的第一个 .lay 文件
        System.out.println("✅ 找到 .lay 文件: " + layFiles[0].getAbsolutePath());

        List<String> cmd = List.of(
                "C:\\Program Files (x86)\\Persyst\\Insight\\PSCLI.exe",
                "/Process",
                // "/MMX=\"C:\\ProgramData\\Persyst\\Trend Settings Research - Export P14.mmx\"",
                "/SourceFile=\"" + layFiles[0].getAbsolutePath() + "\""
        );
        int exitCode = runCommand(cmd);

        if (exitCode != 0) {
            return false;
        }

        // move to S output and delete
        boolean result = moveToSmbShare(task, "");
        if (!result) { return false; }

        return true;
    }

    private boolean arEDF(Task task) {
        if (!moveFromSmbShare(task)) { return false; }

        String sourcePath = task.getSourcePath();
        Path targetDir = Paths.get("D:\\swap_space", String.valueOf(task.getId()), sourcePath);
        // 3. 查找 .lay 文件
        File[] layFiles = targetDir.toFile().listFiles(f ->
                f.isFile() && f.getName().toLowerCase().endsWith(".lay"));

        if (layFiles == null || layFiles.length == 0) {
            System.err.println("⚠️ 未找到 .lay 文件");
            return false;
        }

        // 打印找到的第一个 .lay 文件
        System.out.println("✅ 找到 .lay 文件: " + layFiles[0].getAbsolutePath());

        List<String> cmd = List.of(
                "C:\\Program Files (x86)\\Persyst\\Insight\\PSCLI.exe",
                "/Archive",
                "/Downsample=Off",
                "/ArtifactReduction=On",
                "/SourceFile=\"" + layFiles[0].getAbsolutePath() + "\"",
                "/OutputFile=\"D:\\output_space\\" + sourcePath + "_ar.edf\""
        );
        int exitCode = runCommand(cmd);

        if (exitCode != 0) {
            return false;
        }

        // move to S output and delete
        boolean result = moveToSmbShare(task, sourcePath + "_ar.edf");
        if (!result) { return false; }

        return true;
    }

    private boolean noArCSV(Task task) {
        if (!moveFromSmbShare(task)) { return false; }

        String sourcePath = task.getSourcePath();
        Path targetDir = Paths.get("D:\\swap_space", String.valueOf(task.getId()), sourcePath);
        // 3. 查找 .lay 文件
        File[] layFiles = targetDir.toFile().listFiles(f ->
                f.isFile() && f.getName().toLowerCase().endsWith(".lay"));

        if (layFiles == null || layFiles.length == 0) {
            System.err.println("⚠️ 未找到 .lay 文件");
            return false;
        }

        // 打印找到的第一个 .lay 文件
        System.out.println("✅ 找到 .lay 文件: " + layFiles[0].getAbsolutePath());

        List<String> cmd = List.of(
                "C:\\Program Files (x86)\\Persyst\\Insight\\PSCLI.exe",
                "/ExportCSV",
                "/ArtifactReduction=Off",
                "/SourceFile=\"" + layFiles[0].getAbsolutePath() + "\"",
                "/OutputFile=\"D:\\output_space\\" + sourcePath + "_noar.csv\""
        );
        int exitCode = runCommand(cmd);

        if (exitCode != 0) {
            return false;
        }

        // move to S output and delete
        boolean result = moveToSmbShare(task, sourcePath + "_noar.csv");
        if (!result) { return false; }

        return true;
    }

    private boolean arCSV(Task task) {
        if (!moveFromSmbShare(task)) { return false; }

        String sourcePath = task.getSourcePath();
        Path targetDir = Paths.get("D:\\swap_space", String.valueOf(task.getId()), sourcePath);
        // 3. 查找 .lay 文件
        File[] layFiles = targetDir.toFile().listFiles(f ->
                f.isFile() && f.getName().toLowerCase().endsWith(".lay"));

        if (layFiles == null || layFiles.length == 0) {
            System.err.println("⚠️ 未找到 .lay 文件");
            return false;
        }

        // 打印找到的第一个 .lay 文件
        System.out.println("✅ 找到 .lay 文件: " + layFiles[0].getAbsolutePath());

        List<String> cmd = List.of(
                "C:\\Program Files (x86)\\Persyst\\Insight\\PSCLI.exe",
                "/ExportCSV",
                "/ArtifactReduction=On",
                "/SourceFile=\"" + layFiles[0].getAbsolutePath() + "\"",
                "/OutputFile=\"D:\\output_space\\" + sourcePath + "_ar.csv\""
        );
        int exitCode = runCommand(cmd);

        if (exitCode != 0) {
            return false;
        }

        // move to S output and delete
        boolean result = moveToSmbShare(task, sourcePath + "_ar.csv");
        if (!result) { return false; }

        return true;
    }

    private boolean processTask(Task task) {
        System.out.println("🗂️ 正在处理任务: " + task);
        task.setStatus(Task.TaskStatus.IN_PROGRESS);
        task.setConsumerId(0L);
        taskRepository.save(task);
        switch (task.getTaskName()) {
            case Archive:
                archiveExecutor.submit(() -> {
                    try {
                        boolean result = archive(task);
                        if (!result) {
                            System.err.println("❌ Archive 任务执行失败: " + task);

                            task.setStatus(Task.TaskStatus.FAILED);
                            taskRepository.save(task);

                            // cleaning
                            String sourcePath = task.getSourcePath();  // 比如 task123 或者 patient001
                            taskFileService.removeTask(sourcePath);

                            // cleaning
                            Long taskFileId = task.getFileId();
                            Path basePath = Paths.get("C:\\ProgramData\\OutputMemfs\\" + taskFileId).toAbsolutePath().normalize();
                            try {
                                // 先杀掉进程
                                ProcessManager.killProcess(String.valueOf(taskFileId));

                                // 递归删除整个目录
                                taskFileService.deleteFolderAsUser(basePath.toString());

                                System.out.println("🗑️ 已删除目录：" + basePath);

                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                            // 通知 A
                            String redisToken = redisTemplate.opsForValue().get("token:");
                            taskService.notifyAFinished(task.getId(), null, null, computerIp + ":8080", redisToken, "failed");
                        }
                    } catch (Exception e) {
                        task.setStatus(Task.TaskStatus.FAILED);
                        taskRepository.save(task);

                        System.err.println("⚠️ Archive 任务抛出异常: " + task);
                        e.printStackTrace();
                        System.err.println("❌ Archive 任务执行失败: " + task);

                        // cleaning
                        String sourcePath = task.getSourcePath();  // 比如 task123 或者 patient001
                        taskFileService.removeTask(sourcePath);

                        // cleaning
                        Long taskFileId = task.getFileId();
                        Path basePath = Paths.get("C:\\ProgramData\\OutputMemfs\\" + taskFileId).toAbsolutePath().normalize();
                        try {
                            // 先杀掉进程
                            ProcessManager.killProcess(String.valueOf(taskFileId));

                            // 递归删除整个目录
                            taskFileService.deleteFolderAsUser(basePath.toString());

                            System.out.println("🗑️ 已删除目录：" + basePath);

                        } catch (IOException ee) {
                            ee.printStackTrace();
                        }

                        // 通知 A
                        String redisToken = redisTemplate.opsForValue().get("token:");
                        taskService.notifyAFinished(task.getId(), null, null, computerIp + ":8080", redisToken, "failed");
                    }
                });
                break;
            case NoArEDF:
                if(!noArEDF(task)) {
                    return false;
                }
                break;
            case Process:
                if(!process(task)) {
                    return false;
                }
                break;
            case ArEDF:
                if(!arEDF(task)) {
                    return false;
                }
                break;
            case NoArCSV:
                if(!noArCSV(task)) {
                    return false;
                }
                break;
            case ArCSV:
                if(!arCSV(task)) {
                    return false;
                }
                break;
            default:
                System.out.println("⚠️ 未知任务类型: " + task.getTaskName());
        }
        return true;
    }

    public void handleTask(Message message) {
        String json = new String(message.getBody(), StandardCharsets.UTF_8);
        System.out.println("✅ 接收到任务: " + json);
        // 处理逻辑...
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(json);
            Task task = new Task();
            task.setId(rootNode.get("id").asLong());
            task.setFileId(rootNode.get("fileId").asLong());
            task.setName(rootNode.get("name").asText());
            task.setTaskName(Task.TaskName.valueOf(rootNode.get("taskName").asText()));
            task.setSourcePath(rootNode.get("sourcePath").asText());
            task.setStatus(Task.TaskStatus.valueOf(rootNode.get("status").asText()));
            task.setCreatedAt(LocalDateTime.parse(rootNode.get("createdAt").asText(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            task.setUpdatedAt(LocalDateTime.parse(rootNode.get("updatedAt").asText(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            boolean result = processTask(task); // 阻塞直到任务完成
            if (result) {
                // 成功 记录
                if (!task.getTaskName().equals(Task.TaskName.Archive)) {
                    task.setStatus(Task.TaskStatus.COMPLETED);
                    taskRepository.save(task);
                    // notify S
                    taskService.notifySTaskFinished(task.getId(), "success");
                }
            } else {
                // 失败 记录
                task.setStatus(Task.TaskStatus.FAILED);
                taskRepository.save(task);
                // cleaning
                String sourcePath = task.getSourcePath();
                cleaning(task, sourcePath);
                // notify S
                taskService.notifySTaskFinished(task.getId(), "failed");
            }
        } catch (JsonProcessingException e) {
            System.err.println("❌ JSON 解析失败: " + e.getMessage());
            e.printStackTrace();
            // 失败 而且无法记录 炸了
        }
    }
}
