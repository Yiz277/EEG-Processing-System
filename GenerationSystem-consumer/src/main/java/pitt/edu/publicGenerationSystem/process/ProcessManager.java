package pitt.edu.publicGenerationSystem.process;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProcessManager {
    private static final Map<String, Long> taskPidMap = new ConcurrentHashMap<>();

    public static void startProcess(String exePath, String taskFileId) throws IOException {
        String username = "desktop-khqcqd7\\99415";
        String password = "090100";

        String psScript = String.join(" ", new String[]{
                "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8;",
                "$pass = ConvertTo-SecureString '" + password + "' -AsPlainText -Force;",
                "$cred = New-Object System.Management.Automation.PSCredential('" + username + "', $pass);",
                "$proc = Start-Process '" + exePath + "' -ArgumentList '" + taskFileId + "' -Credential $cred -PassThru;",
                "Write-Output $proc.Id;"
        });

        String[] command = {"powershell.exe", "-NoProfile", "-Command", psScript};
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    long pid = Long.parseLong(line.trim());
                    taskPidMap.put(taskFileId, pid);
                    System.out.println("✅ 启动成功，taskFileId [" + taskFileId + "] 的 PID 为: " + pid);
                    break;
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    public static void killProcess(String taskFileId) throws IOException {
        Long pid = taskPidMap.get(taskFileId);
        if (pid != null) {
            System.out.println("🛑 正在杀掉 taskFileId [" + taskFileId + "] 的 PID: " + pid);
            new ProcessBuilder("taskkill", "/PID", String.valueOf(pid), "/F").start();
            taskPidMap.remove(taskFileId);
        } else {
            System.out.println("⚠️ taskFileId [" + taskFileId + "] 没有记录 PID，无法终止");
        }
    }

    public static void killAll() throws IOException {
        for (Map.Entry<String, Long> entry : taskPidMap.entrySet()) {
            System.out.println("🛑 杀掉 taskFileId [" + entry.getKey() + "] 的 PID: " + entry.getValue());
            new ProcessBuilder("taskkill", "/PID", String.valueOf(entry.getValue()), "/F").start();
        }
        taskPidMap.clear();
    }
}
