/**
 * Módulo shared: kernel mínimo compartilhado — tipos, value objects, erros e geoutils.
 * Não deve conter regras de negócio de nenhum domínio.
 *
 * <p>É um módulo aberto: os demais módulos podem depender do seu conteúdo sem
 * passar por um pacote {@code api}.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Shared kernel",
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.maraberto.shared;
