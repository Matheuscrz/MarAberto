package com.maraberto.audit.api;

/**
 * Ações registradas na trilha (ADR-027).
 *
 * <p>São strings estáveis, não um enum: um módulo novo pode registrar a sua
 * ação sem alterar o contrato do módulo {@code audit}. O valor fica gravado na
 * coluna {@code action} e é o filtro da tela de auditoria.
 */
public final class AuditActions {

    public static final String USER_CREATED = "USER_CREATED";
    public static final String USER_ACTIVATED = "USER_ACTIVATED";
    public static final String USER_DEACTIVATED = "USER_DEACTIVATED";
    public static final String USER_ROLE_CHANGED = "USER_ROLE_CHANGED";
    public static final String USER_PASSWORD_RESET = "USER_PASSWORD_RESET";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String STATION_ACTIVATED = "STATION_ACTIVATED";
    public static final String STATION_DEACTIVATED = "STATION_DEACTIVATED";
    public static final String SHIFT_CLOSED_IDLE = "SHIFT_CLOSED_IDLE";
    public static final String IMPORT_APPLIED = "IMPORT_APPLIED";
    public static final String UNMAPPED_SIGNAL_DECIDED = "UNMAPPED_SIGNAL_DECIDED";
    public static final String OCCURRENCE_CLOSED = "OCCURRENCE_CLOSED";
    public static final String OCCURRENCE_EDITED = "OCCURRENCE_EDITED";
    public static final String PERSON_REVEALED = "PERSON_REVEALED";
    public static final String CONTENT_CREATED = "CONTENT_CREATED";
    public static final String CONTENT_UPDATED = "CONTENT_UPDATED";
    public static final String CONTENT_PUBLISHED = "CONTENT_PUBLISHED";
    public static final String CONTENT_UNPUBLISHED = "CONTENT_UNPUBLISHED";
    public static final String CONTENT_REORDERED = "CONTENT_REORDERED";
    public static final String CONTENT_DELETED = "CONTENT_DELETED";

    private AuditActions() {
    }
}
