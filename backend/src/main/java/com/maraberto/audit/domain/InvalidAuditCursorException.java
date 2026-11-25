package com.maraberto.audit.domain;

/**
 * Cursor opaco da paginação da trilha inválido.
 */
public class InvalidAuditCursorException extends RuntimeException {

    public InvalidAuditCursorException() {
        super("Cursor de auditoria inválido");
    }
}
