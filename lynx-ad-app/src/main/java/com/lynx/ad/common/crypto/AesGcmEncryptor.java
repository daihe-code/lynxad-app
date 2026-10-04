package com.lynx.ad.common.crypto;

import com.lynx.ad.common.config.LynxAdProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class AesGcmEncryptor implements Encryptor {

    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmEncryptor(LynxAdProperties properties) {
        byte[] raw = properties.getCrypto().getAesKey().getBytes(StandardCharsets.UTF_8);
        this.key = new byte[32];
        System.arraycopy(raw, 0, this.key, 0, Math.min(32, raw.length));
    }

    @Override
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherAndTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + cipherAndTag.length);
            buf.put(iv);
            buf.put(cipherAndTag);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception ex) {
            throw new IllegalStateException("encrypt failed", ex);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        try {
            byte[] packed = Base64.getDecoder().decode(ciphertext);
            ByteBuffer buf = ByteBuffer.wrap(packed);
            byte[] iv = new byte[IV_LEN];
            buf.get(iv);
            byte[] cipherAndTag = new byte[buf.remaining()];
            buf.get(cipherAndTag);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherAndTag), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("decrypt failed", ex);
        }
    }
}
