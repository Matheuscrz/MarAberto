package com.maraberto.audit.api;

/**
 * Tipos de entidade gravados na coluna {@code entity_type} (ADR-027).
 *
 * <p>Strings estáveis: a tela de auditoria filtra por elas e o valor não pode
 * mudar sem migração do histórico.
 */
public final class AuditEntityTypes {

    public static final String USER = "USER";
    public static final String OCCURRENCE = "OCCURRENCE";
    public static final String STATION = "STATION";
    public static final String UNMAPPED_SIGNAL = "UNMAPPED_SIGNAL";
    public static final String BATHING_AREA_IMPORT = "BATHING_AREA_IMPORT";
    public static final String SHIFT = "SHIFT";
    public static final String CONTENT = "CONTENT";

    private AuditEntityTypes() {
    }
}
