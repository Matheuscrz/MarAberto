package com.maraberto.identity.infra.security;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Gerador e verificador TOTP compatível com RFC 6238 (SHA-1, 6 dígitos, janela
 * de 30 s, tolerância de ±1 passo).
 */
@Component
public class TotpGenerator {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int SECRET_BYTES = 20;
    private static final long TIME_STEP_SECONDS = 30L;
    private static final int DIGITS = 6;
    private static final int ALLOWED_DRIFT_STEPS = 1;

    private final SecureRandom random = new SecureRandom();

    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    public boolean verifyCode(String base32Secret, String code, Instant now) {
        if (base32Secret == null || base32Secret.isBlank() || code == null) {
            return false;
        }
        String digits = code.replaceAll("\\s+", "");
        if (digits.length() != DIGITS || !digits.chars().allMatch(Character::isDigit)) {
            return false;
        }
        byte[] key = decodeBase32(base32Secret);
        long currentStep = now.getEpochSecond() / TIME_STEP_SECONDS;
        byte[] provided = digits.getBytes(StandardCharsets.US_ASCII);
        for (int offset = -ALLOWED_DRIFT_STEPS; offset <= ALLOWED_DRIFT_STEPS; offset++) {
            String expected = generateCodeForStep(key, currentStep + offset);
            if (MessageDigest.isEqual(provided, expected.getBytes(StandardCharsets.US_ASCII))) {
                return true;
            }
        }
        return false;
    }

    public String currentCode(String base32Secret, Instant now) {
        return generateCodeForStep(decodeBase32(base32Secret), now.getEpochSecond() / TIME_STEP_SECONDS);
    }

    public String buildOtpAuthUri(String issuer, String accountLabel, String base32Secret) {
        String encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedAccount = URLEncoder.encode(accountLabel, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + encodedIssuer + ":" + encodedAccount
                + "?secret=" + base32Secret
                + "&issuer=" + encodedIssuer
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + TIME_STEP_SECONDS;
    }

    private static String generateCodeForStep(byte[] key, long step) {
        try {
            byte[] data = ByteBuffer.allocate(8).putLong(step).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            int otp = binary % 1_000_000;
            return String.format("%06d", otp);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Falha ao calcular código TOTP", exception);
        }
    }

    static String encodeBase32(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1f;
                bitsLeft -= 5;
                out.append(BASE32_ALPHABET.charAt(index));
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1f;
            out.append(BASE32_ALPHABET.charAt(index));
        }
        return out.toString();
    }

    static byte[] decodeBase32(String base32) {
        String normalized = base32.trim().replace("=", "").replaceAll("\\s+", "").toUpperCase();
        byte[] out = new byte[(normalized.length() * 5) / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int pos = 0;
        for (int i = 0; i < normalized.length(); i++) {
            int val = BASE32_ALPHABET.indexOf(normalized.charAt(i));
            if (val < 0) {
                throw new IllegalArgumentException("Segredo Base32 inválido");
            }
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out[pos++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        return out;
    }
}
