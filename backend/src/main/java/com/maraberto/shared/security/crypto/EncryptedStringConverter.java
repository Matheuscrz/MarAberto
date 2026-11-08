package com.maraberto.shared.security.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converte campos {@link String} sensíveis para ciphertext AES-GCM no banco.
 *
 * <p>Uso: {@code @Convert(converter = EncryptedStringConverter.class)} na
 * entidade. O Hibernate instancia o converter fora do ciclo do Spring; por isso
 * o {@link FieldEncryptor} é injetado estaticamente por
 * {@link CryptoConfiguration}. O AAD é fixo por coluna lógica; para vincular o
 * ciphertext a um registro específico, cifre no serviço com um AAD próprio.
 *
 * <p>Valores legados em claro continuam legíveis ({@code decryptOrPlaintext})
 * para permitir migração gradual; novas gravações sempre saem cifradas.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static volatile FieldEncryptor encryptor;

    public static void configure(FieldEncryptor fieldEncryptor) {
        encryptor = fieldEncryptor;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        return requireEncryptor().encrypt(attribute, SensitiveDataProtector.AAD_JPA_FIELD);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return requireEncryptor().decryptOrPlaintext(dbData, SensitiveDataProtector.AAD_JPA_FIELD);
    }

    private static FieldEncryptor requireEncryptor() {
        FieldEncryptor current = encryptor;
        if (current == null) {
            throw new IllegalStateException(
                    "EncryptedStringConverter não configurado; verifique o boot da aplicação");
        }
        return current;
    }
}
