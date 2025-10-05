package pitt.edu.publicGenerationSystem.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        return new RabbitTemplate(connectionFactory);
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean public Queue taskQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-max-priority", 6);
        return new Queue("tasks", true, false, false, args);
    }

    @Bean public Queue anonymizeTaskQueue() {
        return new Queue("anonymize_task", true, false, false);
    }

    @Bean public Queue compressTaskQueue() {
        return new Queue("compress_task", true, false, false);
    }
}
