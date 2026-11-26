package com.maraberto.identity.domain;

import com.maraberto.shared.security.crypto.EncryptedStringConverter;
import com.maraberto.shared.security.crypto.SensitiveDataProtector;
import jakarta.persistence.Converter;

/**
 * Converters JPA com AAD específico por coluna em {@code operational_users}.
 *
 * <p>Impede que um ciphertext de uma coluna (ex.: {@code email}) seja movido
 * para outra coluna (ex.: {@code name} ou {@code cpf}) sem falhar na
 * autenticação do tag AES-GCM.
 */
public final class OperationalUserFieldConverters {

    private OperationalUserFieldConverters() {
    }

    @Converter
    public static class NameConverter extends EncryptedStringConverter {
        public NameConverter() {
            super(SensitiveDataProtector.AAD_USER_NAME);
        }
    }

    @Converter
    public static class EmailConverter extends EncryptedStringConverter {
        public EmailConverter() {
            super(SensitiveDataProtector.AAD_USER_EMAIL);
        }
    }

    @Converter
    public static class CpfConverter extends EncryptedStringConverter {
        public CpfConverter() {
            super(SensitiveDataProtector.AAD_USER_CPF);
        }
    }

    @Converter
    public static class TotpSecretConverter extends EncryptedStringConverter {
        public TotpSecretConverter() {
            super(SensitiveDataProtector.AAD_USER_TOTP_SECRET);
        }
    }
}
