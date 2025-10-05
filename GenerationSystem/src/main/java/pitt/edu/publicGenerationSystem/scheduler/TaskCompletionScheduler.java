package pitt.edu.publicGenerationSystem.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pitt.edu.publicGenerationSystem.service.TaskService;

@Component
public class TaskCompletionScheduler {
    private final TaskService taskService;

    public TaskCompletionScheduler(TaskService taskService) {
        this.taskService = taskService;
    }

    @Scheduled(fixedRate = 5 * 1000) // 每5分钟执行一次（单位：毫秒）
    public void scheduleTask() {
        taskService.taskCompletionScheduler();
        taskService.warehousesCompletionScheduler();
    }
}
