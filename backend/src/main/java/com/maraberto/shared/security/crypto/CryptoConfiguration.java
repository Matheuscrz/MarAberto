package com.maraberto.shared.security.crypto;

import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Configura os serviços de criptografia e valida a chave mestra no boot.
 *
 * <p>A aplicação falha ao subir se a chave estiver ausente, se não tiver 32
 * bytes ou se a chave de desenvolvimento for usada no perfil {@code prod}. Isso
 * evita o pior cenário: rodar em produção com uma chave pública do repositório.
 */
@Configuration
@EnableConfigurationProperties(CryptoProperties.class)
public class CryptoConfiguration {

    static final String DEV_KEY_BASE64 = "ZGV2LW9ubHktbWFyYWJlcnRvLWtleS0zMmJ5dGVzISE=";

    private static final Logger log = LoggerFactory.getLogger(CryptoConfiguration.class);

    private final CryptoProperties properties;
    private final Environment environment;

    public CryptoConfiguration(CryptoProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    void validateConfiguration() {
        String masterKey = properties.getMasterKey();
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException(
                    "maraberto.security.crypto.master-key é obrigatório; defina CRYPTO_MASTER_KEY");
        }

        boolean production = environment.acceptsProfiles(Profiles.of("prod", "production"));
        if (DEV_KEY_BASE64.equals(masterKey.trim())) {
            if (production) {
                throw new IllegalStateException(
                        "a chave de desenvolvimento não pode ser usada no perfil de produção; "
                                + "defina CRYPTO_MASTER_KEY com um segredo real");
            }
            log.warn("Usando a chave de cifragem de desenvolvimento. "
                    + "Defina CRYPTO_MASTER_KEY em qualquer ambiente compartilhado.");
        }

        byte[] decoded = FieldEncryptor.decodeKey(masterKey);
        if (decoded.length != 32) {
            throw new IllegalStateException(
                    "CRYPTO_MASTER_KEY deve decodificar para exatamente 32 bytes (AES-256)");
        }
    }

    @Bean
    FieldEncryptor fieldEncryptor() {
        Map<String, byte[]> previousKeys = new LinkedHashMap<>();
        properties.getPreviousKeys().forEach(
                (keyId, key) -> previousKeys.put(keyId, FieldEncryptor.decodeKey(key)));

        FieldEncryptor encryptor = new FieldEncryptor(
                properties.getKeyId(),
                FieldEncryptor.decodeKey(properties.getMasterKey()),
                previousKeys);
        EncryptedStringConverter.configure(encryptor);
        return encryptor;
    }

    @Bean
    BlindIndexer blindIndexer() {
        return new BlindIndexer(FieldEncryptor.decodeKey(properties.getMasterKey()));
    }

    @Bean
    SensitiveDataProtector sensitiveDataProtector(
            FieldEncryptor fieldEncryptor, BlindIndexer blindIndexer) {
        return new SensitiveDataProtector(fieldEncryptor, blindIndexer);
    }
}
