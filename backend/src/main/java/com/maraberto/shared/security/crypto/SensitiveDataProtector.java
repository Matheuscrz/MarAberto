package com.maraberto.shared.security.crypto;

import java.util.Locale;

/**
 * Fachada de proteção de dados sensíveis usada pelos módulos de domínio.
 *
 * <p>Concentra cifragem, índice cego e mascaramento para que nenhum módulo
 * invente a própria criptografia. Use sempre esta classe (ou
 * {@link FieldEncryptor}/{@link BlindIndexer} diretamente) em vez de
 * {@code MessageDigest}, {@code Base64} ou criptografia caseira.
 */
public class SensitiveDataProtector {

    public static final String AAD_EMERGENCY_OUTBOX = "maraberto:emergency-outbox:v1";
    public static final String AAD_JPA_FIELD = "maraberto:jpa-field:v1";
    public static final String AAD_OCCURRENCE_SUMMARY = "maraberto:occurrence-summary:v1";
    public static final String AAD_OCCURRENCE_OUTCOME = "maraberto:occurrence-outcome:v1";
    public static final String AAD_OCCURRENCE_VICTIM = "maraberto:occurrence-victim:v1";
    public static final String REDACTED = "[REDACTED]";

    private final FieldEncryptor encryptor;
    private final BlindIndexer blindIndexer;

    public SensitiveDataProtector(FieldEncryptor encryptor, BlindIndexer blindIndexer) {
        this.encryptor = encryptor;
        this.blindIndexer = blindIndexer;
    }

    public String encrypt(String value) {
        return encryptor.encrypt(value);
    }

    public String encrypt(String value, String aad) {
        return encryptor.encrypt(value, aad);
    }

    public String decrypt(String value) {
        return encryptor.decrypt(value);
    }

    public String decrypt(String value, String aad) {
        return encryptor.decrypt(value, aad);
    }

    /**
     * Decifra valor gravado por este serviço ou aceita texto legado em claro.
     *
     * <p>Use em leitura de coluna que já teve valor em claro antes da cifragem
     * (migração gradual) e em campos escritos por converter JPA, nunca para
     * validar autenticidade de um payload novo.
     */
    public String decryptOrPlaintext(String value, String aad) {
        return encryptor.decryptOrPlaintext(value, aad);
    }

    public String blindIndex(String value) {
        return blindIndexer.index(value);
    }

    public String blindIndexDigits(String value) {
        return blindIndexer.indexDigits(value);
    }

    public boolean blindIndexMatches(String value, String expectedIndex) {
        return blindIndexer.matches(value, expectedIndex);
    }

    public String keyId() {
        return encryptor.keyId();
    }

    /**
     * Mascara CPF para exibição/log: {@code ***.123.456-**}.
     */
    public String maskCpf(String cpf) {
        String digits = BlindIndexer.digits(cpf);
        if (digits == null || digits.length() != 11) {
            return REDACTED;
        }
        return "***." + digits.substring(3, 6) + "." + digits.substring(6, 9) + "-**";
    }

    /**
     * Mascara e-mail para exibição/log: {@code a***@dominio}.
     */
    public String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return REDACTED;
        }
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return REDACTED;
        }
        return email.charAt(0) + "***@" + email.substring(at + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Mascara telefone para exibição/log, preservando apenas os 4 últimos dígitos.
     */
    public String maskPhone(String phone) {
        String digits = BlindIndexer.digits(phone);
        if (digits == null || digits.length() < 4) {
            return REDACTED;
        }
        return "***" + digits.substring(digits.length() - 4);
    }

    /**
     * Reduz a precisão de coordenadas para logs/analytics (≈110 m).
     * A localização precisa não deve ser registrada em log.
     */
    public String redactLocation(double latitude, double longitude) {
        return String.format(Locale.ROOT, "%.3f,%.3f", latitude, longitude);
    }

    public String redactIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return REDACTED;
        }
        int lastDot = ip.lastIndexOf('.');
        int lastColon = ip.lastIndexOf(':');
        int cut = Math.max(lastDot, lastColon);
        return cut > 0 ? ip.substring(0, cut) + ".***" : REDACTED;
    }
}
