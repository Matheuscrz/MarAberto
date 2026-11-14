package com.maraberto.media.api;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Interface pública do módulo media para os demais módulos.
 *
 * <p>Quem associa mídia a um registro de outro módulo (a foto da sinalização,
 * por exemplo) usa este contrato para confirmar que a imagem existe e que foi
 * enviada pela mesma pessoa — ninguém anexa a foto de outro operador.
 */
public interface MediaCatalog {

    /**
     * Ids existentes e de propriedade do operador informado.
     *
     * @return subconjunto de {@code ids}; tamanho menor significa id inválido
     *         ou de outra pessoa
     */
    Set<UUID> findOwned(UUID ownerId, Collection<UUID> ids);
}
