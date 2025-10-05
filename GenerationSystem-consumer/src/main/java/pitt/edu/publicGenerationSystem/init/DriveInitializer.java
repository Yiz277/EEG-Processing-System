package pitt.edu.publicGenerationSystem.init;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.io.Console;


@Component
public class DriveInitializer implements CommandLineRunner {
    @Value("${ip.computerS}")
    private String computerSIp;


    @Override
    public void run(String... args) throws Exception {
        // 确保允许 Swing 弹窗
        System.setProperty("java.awt.headless", "false");

        while (true) {
            // 用户名输入框
            JTextField usernameField = new JTextField("BrainFluxSharer");
            // 密码输入框（不显示明文）
            JPasswordField passwordField = new JPasswordField();
            Object[] message = {
                    "Username:", usernameField,
                    "Password:", passwordField
            };

            int option = JOptionPane.showConfirmDialog(
                    null,
                    message,
                    "SMB 登录",
                    JOptionPane.OK_CANCEL_OPTION
            );

            if (option != JOptionPane.OK_OPTION) {
                System.err.println("❌ 用户取消挂载");
                System.exit(1);
            }

            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(null,
                        "用户名或密码不能为空",
                        "输入错误",
                        JOptionPane.ERROR_MESSAGE);
                continue;
            }

            // 构造 net use 命令
            String command = String.format(
                    "net use \\\\" + computerSIp + "\\shared_space /user:%s %s",
                    username, password
            );

            try {
                ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
                pb.inheritIO(); // 输出继承到控制台
                Process process = pb.start();
                int exitCode = process.waitFor();

                if (exitCode == 0) {
                    JOptionPane.showMessageDialog(null,
                            "✅ 网络驱动器挂载成功",
                            "成功",
                            JOptionPane.INFORMATION_MESSAGE);
                    System.out.println("✅ Drive mounted successfully.");
                    break;
                } else {
                    JOptionPane.showMessageDialog(null,
                            "❌ 挂载失败，退出码: " + exitCode,
                            "错误",
                            JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null,
                        "❌ 执行命令出错: " + e.getMessage(),
                        "异常",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
