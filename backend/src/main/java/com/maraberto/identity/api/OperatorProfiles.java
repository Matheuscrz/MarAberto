package com.maraberto.identity.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consulta de perfis de operadores por identificador.
 *
 * <p>Existe para que telas autorizadas de outros módulos — como o painel da
 * coordenação (RF40/RN17) — mostrem nome e matrícula sem decifrar o cadastro
 * fora do {@code identity}. O nome só é devolvido em rota autenticada.
 */
public interface OperatorProfiles {

    /** Perfil do operador, se ele existir. */
    Optional<OperatorProfile> find(UUID userId);

    /** Perfis dos operadores existentes; identificadores desconhecidos são omitidos. */
    List<OperatorProfile> findAll(Collection<UUID> userIds);
}
