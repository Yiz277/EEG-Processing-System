package pitt.edu.publicGenerationSystem.utils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;

import javax.crypto.Cipher;

public class RSAUtil {

    // 从资源目录中加载私钥（PEM 文件）
    public static PrivateKey loadPrivateKeyFromResource(String resourceName) throws Exception {
        InputStream is = RSAUtil.class.getClassLoader().getResourceAsStream(resourceName);
        byte[] keyBytes = is.readAllBytes();
        String key = new String(keyBytes, StandardCharsets.UTF_8)
                .replaceAll("-----BEGIN PRIVATE KEY-----", "")
                .replaceAll("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(key);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }

    // 从资源目录中加载公钥（PEM 文件）
    public static PublicKey loadPublicKeyFromResource(String resourceName) throws Exception {
        InputStream is = RSAUtil.class.getClassLoader().getResourceAsStream(resourceName);
        byte[] keyBytes = is.readAllBytes();
        String key = new String(keyBytes, StandardCharsets.UTF_8)
                .replaceAll("-----BEGIN PUBLIC KEY-----", "")
                .replaceAll("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(key);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePublic(keySpec);
    }

    // 用私钥加密（适用于你当前：A 用私钥加密 JWT）
    public static String encryptWithPrivateKey(String data, PrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, privateKey);
        byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    // 用公钥解密（B 解密 token）
    public static String decryptWithPublicKey(String base64Encrypted, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, publicKey);
        byte[] decodedBytes = Base64.getDecoder().decode(base64Encrypted);
        byte[] decrypted = cipher.doFinal(decodedBytes);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    // ✅ 可选：检测加密长度是否超出限制
    public static boolean isSafeToEncrypt(String text, Key key) throws Exception {
        int maxBytes = Cipher.getInstance("RSA").getBlockSize();
        return text.getBytes(StandardCharsets.UTF_8).length <= maxBytes;
    }
}
