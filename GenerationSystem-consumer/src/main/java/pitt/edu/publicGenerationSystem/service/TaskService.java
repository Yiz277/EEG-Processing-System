package pitt.edu.publicGenerationSystem.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import pitt.edu.publicGenerationSystem.DTO.FileMeta;
import pitt.edu.publicGenerationSystem.DTO.FileReadyDto;
import pitt.edu.publicGenerationSystem.entity.Task;
import pitt.edu.publicGenerationSystem.repository.TaskRepository;
import pitt.edu.publicGenerationSystem.response.ApiResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TaskService {

    @Autowired
    private TaskRepository taskRepository;

    @Value("${ip.computerA}")
    private String computerAIp;

    @Value("${ip.computerS}")
    private String computerSIp;

    @Transactional
    public Task createTask(Long fileId, String processedName, Task.TaskName taskName, String sourcePath, Task.TaskStatus status) {
        Task task = new Task();
        task.setFileId(fileId);
        task.setName(processedName);
        task.setTaskName(taskName);
        task.setSourcePath(sourcePath);
        task.setStatus(status);
        return taskRepository.save(task);
    }

    public List<Task> getTasksByTaskNameAndStatusIn(Task.TaskName taskName, List<Task.TaskStatus> statuses) {
        return taskRepository.findByTaskNameAndStatusIn(
                taskName,
                statuses
        );
    }

    public List<Task> getTasksByTaskNameAndStatus(Task.TaskName taskName, Task.TaskStatus status) {
        return taskRepository.findByTaskNameAndStatus(
                taskName,
                status
        );
    }

    public Task updateTask(Task task) {
        return taskRepository.save(task);
    }

    public boolean notifyAFinished(Long taskId, String sourcePath, List<FileMeta> files, String baseUrl, String token, String status) {
        String url = "http://" + computerAIp + ":8080/api/v1/archive-file-finished";  // 本机 A 的控制接口地址

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token);

        FileReadyDto dto = new FileReadyDto();
        dto.setTaskId(taskId);
        dto.setSourcePath(sourcePath);
        dto.setStatus(status);
        dto.setMessage("All files are ready.");
        dto.setFiles(files);
        dto.setBaseUrl(baseUrl);

        HttpEntity<FileReadyDto> request = new HttpEntity<>(dto, headers);

        try {
            ResponseEntity<ApiResponse> response = new RestTemplate().postForEntity(url, request, ApiResponse.class);
            System.out.println("✅ 成功通知 A: " + response.getStatusCode());
            if (response.getStatusCode() == HttpStatus.OK) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            System.err.println("❌ 通知 A 失败");
            e.printStackTrace();
            return false;
        }
    }

    public boolean notifySTaskFinished(Long taskId, String status) {
        String url = "http://" + computerSIp + ":8080/api/v1/file-task-finished";

        Map<String, Object> body = new HashMap<>();
        body.put("task_id", taskId);
        body.put("status", status);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer ");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Void> resp = restTemplate.exchange(
                url, HttpMethod.POST, entity, Void.class
        );

        if (!resp.getStatusCode().is2xxSuccessful()) {
            System.out.println("❌ S 返回非 2xx: " + resp.getStatusCode());
            return false;
        }
        return true;
    }
}