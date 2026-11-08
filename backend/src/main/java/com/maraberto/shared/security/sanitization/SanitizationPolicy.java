package com.maraberto.shared.security.sanitization;

/**
 * Políticas de sanitização aplicáveis a dados que chegam da web.
 *
 * <p>Escolha a política mais restritiva que atende ao caso de uso. Textos livres
 * usam {@link #PLAIN_TEXT}; conteúdo editorial usa {@link #RICH_TEXT};
 * identificadores, e-mails, telefones, CPF, URLs e IDs de vídeo têm regras
 * próprias.
 */
public enum SanitizationPolicy {
    PLAIN_TEXT,
    RICH_TEXT,
    IDENTIFIER,
    EMAIL,
    PHONE,
    CPF,
    URL,
    YOUTUBE_ID
}
