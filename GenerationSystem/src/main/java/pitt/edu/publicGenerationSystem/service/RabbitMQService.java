package pitt.edu.publicGenerationSystem.service;

import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import pitt.edu.publicGenerationSystem.entity.Task;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RabbitMQService {
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public RabbitMQService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    private int getPriority(Task.TaskName name) {
        return switch (name) {
            case Archive -> 0;
            case NoArEDF -> 1;
            case Process -> 2;
            case ArEDF -> 3;
            case NoArCSV -> 4;
            case ArCSV -> 5;
            case Compression -> 6;
            case Anonymization -> 7;
        };
    }

    /**
     * 发送 Task 到 RabbitMQ 队列
     */
    public void sendTask(Task task) {
        try {
            Map<String, String> message = new LinkedHashMap<>();

            message.put("id", String.valueOf(task.getId()));
            message.put("fileId", String.valueOf(task.getFileId()));
            message.put("name", task.getName());
            message.put("taskName", task.getTaskName().name());
            message.put("consumerId", String.valueOf(task.getConsumerId()));
            message.put("sourcePath", task.getSourcePath());
            message.put("status", task.getStatus().name());
            message.put("createdAt", task.getCreatedAt() != null ? task.getCreatedAt().format(TIME_FORMATTER) : null);
            message.put("updatedAt", task.getUpdatedAt() != null ? task.getUpdatedAt().format(TIME_FORMATTER) : null);

            String queueName = "";
            String json = objectMapper.writeValueAsString(message);

            int priority = getPriority(task.getTaskName());

            if (priority < 6) {
                queueName = "tasks";
                rabbitTemplate.convertAndSend(queueName, json, msg -> {
                    msg.getMessageProperties().setPriority(priority);
                    return msg;
                });
            } else if (priority == 6) {
                queueName = "compress_task";
                rabbitTemplate.convertAndSend(queueName, json, msg -> {
                    return msg;
                });
            } else {
                queueName = "anonymize_task";
                rabbitTemplate.convertAndSend(queueName, json, msg -> {
                    return msg;
                });
            }

            System.out.println("✔ 发送到队列 [" + queueName + "] 优先级 " + priority + ": " + json);

        } catch (Exception e) {
            System.err.println("❌ 发送失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
