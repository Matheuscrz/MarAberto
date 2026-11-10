package com.maraberto.identity.domain;

/**
 * Perfis de acesso do efetivo operacional.
 *
 * <p>Os nomes são os mesmos usados no frontend e no claim {@code role} do JWT
 * (ADR-009): {@code LIFEGUARD} → {@code /operador}, {@code COORDINATOR} →
 * {@code /gestao}, {@code ADMIN} → {@code /admin}. As autoridades do Spring
 * Security são derivadas com o prefixo {@code ROLE_}.
 */
public enum Role {
    LIFEGUARD,
    COORDINATOR,
    ADMIN;

    /** Autoridade no formato aceito por {@code hasRole}/hasAnyRole}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
