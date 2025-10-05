
package pitt.edu.publicGenerationSystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pitt.edu.publicGenerationSystem.service.TaskService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "http://localhost:8080", allowedHeaders = "*")
public class TaskController {

    @Autowired
    private TaskService taskService;
}
