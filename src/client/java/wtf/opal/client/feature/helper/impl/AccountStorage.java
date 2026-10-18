package wtf.opal.client.feature.helper.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import wtf.opal.client.Constants;
import wtf.opal.client.feature.helper.impl.auth.Account;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 账号信息加密存储类
 * 使用 AES-GCM 算法加密账号数据
 */
public class AccountStorage {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int GCM_IV_LENGTH = 12; // bytes
    private static final int AES_KEY_SIZE = 256; // bits

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey encryptionKey;
    private final File storageFile;

    public AccountStorage() {
        this.encryptionKey = deriveKey();
        this.storageFile = new File(Constants.getDirectory(), "accounts.enc");
    }

    /**
     * 从机器标识派生加密密钥
     */
    private SecretKey deriveKey() {
        try {
            // 使用用户名 + 主机名作为密钥种子
            String machineId = System.getProperty("user.name") + "@" + System.getProperty("user.home");

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(machineId.getBytes(StandardCharsets.UTF_8));

            // 使用前32字节作为AES-256密钥
            byte[] keyBytes = new byte[32];
            System.arraycopy(hash, 0, keyBytes, 0, 32);

            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            // 如果派生失败，生成随机密钥（但每次重启会丢失数据）
            KeyGenerator keyGen;
            try {
                keyGen = KeyGenerator.getInstance("AES");
                keyGen.init(AES_KEY_SIZE);
                return keyGen.generateKey();
            } catch (Exception ex) {
                throw new RuntimeException("Failed to generate encryption key", ex);
            }
        }
    }

    /**
     * 保存账号列表（加密）
     */
    public void saveAccounts(List<Account> accounts) throws AuthenticationException {
        try {
            // 序列化为JSON
            String json = GSON.toJson(accounts);

            // 生成随机IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            RANDOM.nextBytes(iv);

            // 加密
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, parameterSpec);

            byte[] encryptedData = cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));

            // 组合: IV + 加密数据
            byte[] combined = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedData, 0, combined, iv.length, encryptedData.length);

            // 写入文件（Base64编码）
            String base64 = Base64.getEncoder().encodeToString(combined);

            // 确保目录存在
            if (!storageFile.getParentFile().exists()) {
                storageFile.getParentFile().mkdirs();
            }

            try (Writer writer = new FileWriter(storageFile)) {
                writer.write(base64);
            }
        } catch (Exception e) {
            throw new AuthenticationException(
                    AuthenticationException.AuthenticationError.STORAGE_ERROR,
                    "Failed to save accounts",
                    e
            );
        }
    }

    /**
     * 加载账号列表（解密）
     */
    public List<Account> loadAccounts() {
        if (!storageFile.exists()) {
            return new ArrayList<>();
        }

        try {
            // 读取文件
            String base64;
            try (Reader reader = new FileReader(storageFile)) {
                char[] buffer = new char[(int) storageFile.length()];
                reader.read(buffer);
                base64 = new String(buffer).trim();
            }

            // Base64解码
            byte[] combined = Base64.getDecoder().decode(base64);

            // 提取IV和加密数据
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedData = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encryptedData, 0, encryptedData.length);

            // 解密
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, parameterSpec);

            byte[] decryptedData = cipher.doFinal(encryptedData);
            String json = new String(decryptedData, StandardCharsets.UTF_8);

            // 反序列化
            return GSON.fromJson(json, new TypeToken<List<Account>>(){}.getType());
        } catch (Exception e) {
            // 解密失败，返回空列表（可能是密钥变更或文件损坏）
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * 清除所有保存的账号数据
     */
    public void clear() {
        if (storageFile.exists()) {
            storageFile.delete();
        }
    }
}
