/**
 * Módulo identity: cadastro, OTP, CPF (cifrado), login, tokens, papéis,
 * administração de usuários pela TI (senha temporária com troca obrigatória)
 * e MFA (TOTP).
 *
 * <p>Não publica eventos de domínio: a administração de usuários é síncrona e
 * a trilha de auditoria é chamada direto pela porta {@code audit/api/AuditTrail}
 * (ADR-027).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Identity")
package com.maraberto.identity;
