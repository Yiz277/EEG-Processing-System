package pitt.edu.publicGenerationSystem.service;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pitt.edu.publicGenerationSystem.consumer.TaskConsumer;

import java.util.Optional;

@Service
public class DynamicListenerManager {

    private final RabbitAdmin rabbitAdmin;
    private final RabbitTemplate rabbitTemplate;
    private final SimpleMessageListenerContainer container;
    private final TaskConsumer taskConsumer;

    @Autowired
    public DynamicListenerManager(
            ConnectionFactory connectionFactory,
            RabbitAdmin rabbitAdmin,
            RabbitTemplate rabbitTemplate,
            TaskConsumer taskConsumer
    ) {
        this.rabbitAdmin = rabbitAdmin;
        this.rabbitTemplate = rabbitTemplate;
        this.taskConsumer = taskConsumer;

        // 初始化 ListenerContainer（不自动启动）
        container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(
                "tasks"
        );
        container.setConcurrentConsumers(48);
        container.setMaxConcurrentConsumers(48);
        container.setAutoStartup(false); // 初始不启动
        container.setMessageListener(taskConsumer::handleTask); // 指定消费者方法
    }

    public void startIfNeeded() {
        int count = Optional.ofNullable(rabbitAdmin.getQueueProperties("compress_task"))
                .map(props -> (int) props.getOrDefault("QUEUE_MESSAGE_COUNT", 0))
                .orElse(1);
        if (count == 0) {
            if (!container.isRunning()) {
                System.out.println("🟢 启动监听器");
                container.start();
            }
        } else {
            if (container.isRunning()) {
                container.setShutdownTimeout(24L * 60 * 60 * 1000 * 3);
                System.out.println("🔴 停止监听器，Compression 队列还有任务");
                container.stop();
            }
        }
    }
}
