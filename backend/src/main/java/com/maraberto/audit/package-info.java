/**
 * Módulo audit: trilha imutável, chamada de forma síncrona na mesma transação
 * do ato auditado, com encadeamento de hash e consulta exclusiva da TI
 * (ADR-027).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Audit")
package com.maraberto.audit;
