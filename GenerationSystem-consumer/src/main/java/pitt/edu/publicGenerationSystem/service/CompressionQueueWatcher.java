package pitt.edu.publicGenerationSystem.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class CompressionQueueWatcher {

    private final DynamicListenerManager listenerManager;

    private volatile boolean enabled = false;

    public CompressionQueueWatcher(DynamicListenerManager listenerManager) {
        this.listenerManager = listenerManager;
    }

    // 开启定时任务的方法（在登录成功后调用）
    public void enable() {
        this.enabled = true;
    }

    @Scheduled(fixedRate = 60_000)
    public void checkAndToggleConsumer() {
        if (!enabled) return;  // 👈 没登录成功前什么都不做
        listenerManager.startIfNeeded();
    }
}
