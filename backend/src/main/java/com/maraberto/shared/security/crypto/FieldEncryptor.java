package com.maraberto.shared.security.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cifragem autenticada de campos sensíveis com AES-256-GCM.
 *
 * <p>Formato do token: {@code enc:v1:<keyId>:<base64url(iv || ciphertext || tag)>}.
 * O IV é aleatório por operação, de 12 bytes, e o tag GCM tem 128 bits. O
 * {@code keyId} permite rotação sem reescrever todos os registros de uma vez.
 *
 * <p>Use {@code aad} (Associated Authenticated Data) para vincular o ciphertext
 * ao contexto de uso (ex.: {@code maraberto:emergency-outbox:v1}). Um ciphertext
 * movido para outro contexto falha na decifragem.
 */
public final class FieldEncryptor {

    private static final String PREFIX = "enc:v1:";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final String currentKeyId;
    private final SecretKeySpec currentKey;
    private final Map<String, SecretKeySpec> keys;
    private final SecureRandom random = new SecureRandom();

    public FieldEncryptor(String currentKeyId, byte[] masterKey, Map<String, byte[]> previousKeys) {
        if (currentKeyId == null || currentKeyId.isBlank()) {
            throw new IllegalArgumentException("keyId é obrigatório");
        }
        if (masterKey == null || masterKey.length != KEY_BYTES) {
            throw new IllegalArgumentException("a chave mestra deve ter 32 bytes (AES-256)");
        }

        this.currentKeyId = currentKeyId;
        this.currentKey = new SecretKeySpec(masterKey, "AES");

        Map<String, SecretKeySpec> allKeys = new LinkedHashMap<>();
        allKeys.put(currentKeyId, currentKey);
        if (previousKeys != null) {
            previousKeys.forEach((keyId, key) -> {
                if (keyId == null || keyId.isBlank()) {
                    throw new IllegalArgumentException("id de chave anterior é obrigatório");
                }
                if (keyId.equals(currentKeyId)) {
                    throw new IllegalArgumentException("a chave atual não pode estar em previousKeys");
                }
                if (key == null || key.length != KEY_BYTES) {
                    throw new IllegalArgumentException("chave anterior '" + keyId + "' deve ter 32 bytes");
                }
                allKeys.put(keyId, new SecretKeySpec(key, "AES"));
            });
        }
        this.keys = Map.copyOf(allKeys);
    }

    public String keyId() {
        return currentKeyId;
    }

    public String encrypt(String plaintext) {
        return encrypt(plaintext, null);
    }

    public String encrypt(String plaintext, String aad) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, currentKey, new GCMParameterSpec(TAG_BITS, iv));
            if (aad != null && !aad.isEmpty()) {
                cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            }

            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);

            return PREFIX + currentKeyId + ":" + ENCODER.encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("não foi possível cifrar o valor", exception);
        }
    }

    public String decrypt(String token) {
        return decrypt(token, null);
    }

    public String decrypt(String token, String aad) {
        if (token == null || token.isEmpty()) {
            return token;
        }
        if (!token.startsWith(PREFIX)) {
            throw new IllegalArgumentException("valor não está no formato cifrado esperado");
        }

        String[] parts = token.split(":", 4);
        if (parts.length != 4) {
            throw new IllegalArgumentException("token cifrado inválido");
        }

        SecretKeySpec key = keys.get(parts[2]);
        if (key == null) {
            throw new IllegalArgumentException("chave de cifragem desconhecida: " + parts[2]);
        }

        byte[] payload;
        try {
            payload = DECODER.decode(parts[3]);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("token cifrado inválido", exception);
        }
        if (payload.length <= IV_BYTES) {
            throw new IllegalArgumentException("token cifrado inválido");
        }

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES));
            if (aad != null && !aad.isEmpty()) {
                cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            }
            byte[] plain = cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException(
                    "não foi possível decifrar o valor (chave ou integridade inválida)", exception);
        }
    }

    /**
     * Decifra valores novos e devolve valores legados em claro. Use apenas em
     * migrações; nunca como caminho normal de leitura de dado sensível.
     */
    public String decryptOrPlaintext(String value, String aad) {
        if (value == null || value.isEmpty() || !value.startsWith(PREFIX)) {
            return value;
        }
        return decrypt(value, aad);
    }

    public static byte[] decodeKey(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalArgumentException("chave mestra não configurada");
        }
        try {
            return Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("chave mestra não é base64 válido", exception);
        }
    }
}
