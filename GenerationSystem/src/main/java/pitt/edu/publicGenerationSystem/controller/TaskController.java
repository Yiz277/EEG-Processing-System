
package pitt.edu.publicGenerationSystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pitt.edu.publicGenerationSystem.response.ApiResponse;
import pitt.edu.publicGenerationSystem.service.TaskService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "http://localhost:8080", allowedHeaders = "*")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @PostMapping("/file-archive-finished")
    public ResponseEntity<ApiResponse> fileArchiveFinished(@RequestHeader("Authorization") String authHeader,
                                                            @RequestBody Map<String, String> requestBody) {
        Long taskId = Long.valueOf(requestBody.get("task_id"));
        String status = requestBody.get("status");

        return taskService.fileArchiveFinished(taskId, status);
    }

    @PostMapping("/file-task-finished")
    public ResponseEntity<ApiResponse> fileTaskFinished(@RequestHeader("Authorization") String authHeader,
                                                           @RequestBody Map<String, String> requestBody) {
        Long taskId = Long.valueOf(requestBody.get("task_id"));
        String status = requestBody.get("status");

        return taskService.fileTaskFinished(taskId, status);
    }

    @PostMapping("/progress-index")
    public ResponseEntity<ApiResponse> progressIndex(@RequestHeader("Authorization") String authHeader,
                                                        @RequestBody Map<String, String> requestBody) {
        return taskService.progressIndex();
    }
}
