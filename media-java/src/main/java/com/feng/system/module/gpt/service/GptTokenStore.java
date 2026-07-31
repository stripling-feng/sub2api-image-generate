package com.feng.system.module.gpt.service;

import com.feng.system.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class GptTokenStore {
    private static final String LEGACY_PREFIX = "v1:";
    private static final int LEGACY_IV_LENGTH = 12;

    @Value("${gpt.legacy-account-encryption-key:}")
    private String legacyPassphrase;

    public String read(String storedToken) {
        if (storedToken == null || !storedToken.startsWith(LEGACY_PREFIX)) {
            return storedToken;
        }
        if (legacyPassphrase == null || legacyPassphrase.length() < 24) {
            throw new BusinessException("检测到旧版加密 Token，请临时配置 GPT_ACCOUNT_LEGACY_ENCRYPTION_KEY 完成明文迁移");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(storedToken.substring(LEGACY_PREFIX.length()));
            byte[] iv = new byte[LEGACY_IV_LENGTH];
            byte[] encrypted = new byte[payload.length - LEGACY_IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, iv.length);
            System.arraycopy(payload, iv.length, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(sha256Bytes(legacyPassphrase), "AES"),
                    new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new BusinessException("旧版 GPT 账号 Token 解密失败，请检查迁移密钥");
        }
    }

    public String hash(String value) {
        return HexFormat.of().formatHex(sha256Bytes(value));
    }

    private static byte[] sha256Bytes(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
