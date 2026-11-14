/**
 * Módulo occurrences: a ficha operacional do atendimento (ADR-013).
 *
 * <p>A ocorrência nasce quando o guarda-vidas assume o chamado do SOS —
 * abertura idempotente a partir de {@code SosAcknowledged}, fora do caminho
 * crítico do alerta — e também receberá, na fatia seguinte, o registro manual
 * do 193 e do atendimento de campo.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Occurrences")
package com.maraberto.occurrences;
