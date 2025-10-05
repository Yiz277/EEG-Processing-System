package pitt.edu.publicGenerationSystem.init;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import pitt.edu.publicGenerationSystem.middleware.JwtUtil;
import pitt.edu.publicGenerationSystem.response.ApiResponse;
import pitt.edu.publicGenerationSystem.service.CompressionQueueWatcher;
import pitt.edu.publicGenerationSystem.utils.RSAUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import javax.swing.*;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component
public class UserInitializer implements CommandLineRunner {
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private CompressionQueueWatcher compressionQueueWatcher;

    @Value("${ip.computerA}")
    private String computerAIp;

    public static String getLocalIPv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();

                if (!iface.isUp() || iface.isLoopback()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    String ip = addr.getHostAddress();

                    if (addr instanceof Inet4Address && ip.startsWith("192.")) {
                        return ip;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "127.0.0.1";  // 没找到就返回回环地址
    }

    public String sendTokenToA(String ip, String AToken) {
        String token = redisTemplate.opsForValue().get("token:");
        if (token == null) {
            System.err.println("❌ 没有可用 token，请先认证");
            return null;
        }

        String url = "http://" + ip + ":8080/api/v1/user-token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);  // token 放在 header

        Map<String, Object> body = new HashMap<>();
        body.put("token", AToken);
//        System.out.println(AToken);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = new RestTemplate();

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            return response.getBody();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void run(String... args) {
        System.setProperty("java.awt.headless", "false");
        String url = "http://" + computerAIp + ":8080/api/v1/user-login";
        RestTemplate restTemplate = new RestTemplate();

        while (true) {
            // 弹出用户名和密码输入框
            JTextField usernameField = new JTextField();
            JPasswordField passwordField = new JPasswordField();
            Object[] message = {
                    "Username:", usernameField,
                    "Password:", passwordField
            };

            int option = JOptionPane.showConfirmDialog(null, message, "Login", JOptionPane.OK_CANCEL_OPTION);
            if (option != JOptionPane.OK_OPTION) {
                System.exit(1);  // 用户取消登录，退出程序
            }

            String username = usernameField.getText();
            String password = new String(passwordField.getPassword());

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("username", username);
            requestBody.put("password", password);
            requestBody.put("ip",  getLocalIPv4());

            try {
                ApiResponse<?> response = restTemplate.postForObject(url, requestBody, ApiResponse.class);
                if (response != null && "success".equals(response.getStatus()) && response.getData() != null) {
                    Map<String, Object> data = (Map<String, Object>) response.getData();
                    String encryptedToken = (String) data.get("token");
                    String ip = (String) data.get("ip");
                    // 加载公钥
                    PublicKey publicKey = RSAUtil.loadPublicKeyFromResource("public_key.pem");
                    // 解密 token（B 收到后）
                    String token = RSAUtil.decryptWithPublicKey(encryptedToken, publicKey);
//                    System.out.println(token);
//                    System.out.println(ip);
                    redisTemplate.opsForValue().set("token:", token);
                    redisTemplate.opsForValue().set("ip:", ip);

                    // 给A一个token
                    String AToken = JwtUtil.generateToken("A");
                    sendTokenToA(ip, AToken);
                    redisTemplate.opsForValue().set("AToken:", AToken);

                    compressionQueueWatcher.enable();
                    System.out.println("✅ Login successful.");
                    break;
                } else {
                    JOptionPane.showMessageDialog(null,
                            "❌ Login failed: " + (response != null ? response.getMessage() : "Unknown error"),
                            "Login Error", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null,
                        "❌ Network error: " + e.getMessage(),
                        "Network Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}

