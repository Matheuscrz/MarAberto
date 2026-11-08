package com.maraberto.shared.security.crypto;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração de criptografia de dados sensíveis.
 *
 * <p>A chave mestra deve ser um segredo de 32 bytes (AES-256) em base64, mantido
 * fora do repositório em qualquer ambiente compartilhado. O {@code key-id}
 * identifica a chave atual e permite rotação: valores antigos continuam legíveis
 * enquanto estiverem em {@link #previousKeys}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "maraberto.security.crypto")
public class CryptoProperties {

    private String keyId = "k1";

    private String masterKey;

    private Map<String, String> previousKeys = new LinkedHashMap<>();
}
