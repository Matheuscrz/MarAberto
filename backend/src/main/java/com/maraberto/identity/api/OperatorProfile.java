package com.maraberto.identity.api;

import java.util.UUID;

/**
 * Identificação funcional mínima de um operador para outros módulos.
 *
 * <p>O nome é cifrado no banco (ADR-012) e só é decifrado dentro do módulo
 * {@code identity}; quem precisa exibir o operador em uma tela autorizada recebe
 * este recorte pronto, sem tocar no cadastro nem na credencial.
 */
public record OperatorProfile(UUID id, String name, String badgeNumber) {
}
