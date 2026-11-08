package com.maraberto.shared.security.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.Base64;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Índice cego (blind index) determinístico para busca de igualdade sobre dados
 * cifrados, sem revelar o valor em claro.
 *
 * <p>O índice é um HMAC-SHA256 com chave derivada da chave mestra e domínio
 * separado. Nunca use hash simples (SHA-256) para CPF/e-mail: o espaço de
 * entrada é pequeno e permitiria ataque de dicionário.
 */
public final class BlindIndexer {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String DERIVATION_CONTEXT = "maraberto:blind-index:v1";
    private static final int KEY_BYTES = 32;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final SecretKeySpec key;

    public BlindIndexer(byte[] masterKey) {
        if (masterKey == null || masterKey.length != KEY_BYTES) {
            throw new IllegalArgumentException("a chave mestra deve ter 32 bytes (AES-256)");
        }
        this.key = new SecretKeySpec(hmac(masterKey, DERIVATION_CONTEXT), HMAC_ALGORITHM);
    }

    public String index(String value) {
        return indexNormalized(normalize(value));
    }

    public String indexDigits(String value) {
        return indexNormalized(digits(value));
    }

    public String indexNormalized(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return ENCODER.encodeToString(hmac(key.getEncoded(), value));
    }

    /**
     * Comparação em tempo constante entre o índice calculado e o esperado.
     */
    public boolean matches(String value, String expectedIndex) {
        String actual = index(value);
        if (actual == null || expectedIndex == null) {
            return false;
        }
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expectedIndex.getBytes(StandardCharsets.UTF_8));
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    public static String digits(String value) {
        return value == null ? null : value.replaceAll("\\D", "");
    }

    private static byte[] hmac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("não foi possível calcular o índice cego", exception);
        }
    }
}
