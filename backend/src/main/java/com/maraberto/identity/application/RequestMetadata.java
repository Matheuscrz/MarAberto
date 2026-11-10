package com.maraberto.identity.application;

/**
 * Metadados da requisição usados na auditoria da sessão.
 *
 * <p>O IP é convertido em índice cego antes de sair deste objeto; o
 * {@code userAgent} é truncado. Nenhum dos dois vira log de acesso.
 */
public record RequestMetadata(String userAgent, String ipAddress) {
}
