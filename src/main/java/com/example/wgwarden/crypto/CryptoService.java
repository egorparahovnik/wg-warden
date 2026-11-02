package com.example.wgwarden.crypto;

import com.example.wgwarden.config.AppProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Service
public class CryptoService {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(AppProperties props) {
        byte[] raw = Base64.getDecoder().decode(props.masterKey());
        if (raw.length != 32) {
            throw new IllegalStateException("APP_MASTER_KEY должен быть 32 байта в base64");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public byte[] encrypt(byte[] plain) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain);
            byte[] result = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, result, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, result, IV_LENGTH, encrypted.length);
            return result;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ошибка шифрования", e);
        }
    }

    public byte[] decrypt(byte[] data) {
        try {
            byte[] iv = Arrays.copyOfRange(data, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(data, IV_LENGTH, data.length - IV_LENGTH);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ошибка расшифровки", e);
        }
    }
}
