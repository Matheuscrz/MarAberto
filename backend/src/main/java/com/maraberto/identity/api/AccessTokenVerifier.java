package com.maraberto.identity.api;

import java.util.Optional;

/**
 * Porta de validação do access token para canais fora do filtro HTTP.
 *
 * <p>Existe para o WebSocket autenticado: o STOMP valida o JWT no frame
 * {@code CONNECT} sem que o módulo {@code realtime} conheça a implementação de
 * tokens do {@code identity} (ADR-018).
 */
public interface AccessTokenVerifier {

    /**
     * @return os dados do token quando ele é válido, assinado e não expirou;
     *         {@link Optional#empty()} caso contrário
     */
    Optional<AccessTokenClaims> verify(String token);
}
