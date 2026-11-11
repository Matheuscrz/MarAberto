package com.maraberto.identity.api;

import java.util.List;
import java.util.UUID;

/**
 * Consulta de operadores ativos por papel, usada para acionar a coordenação
 * quando não há ninguém elegível em campo (RN03).
 */
public interface OperatorDirectory {

    /** Identificadores dos coordenadores ativos. */
    List<UUID> activeCoordinationUserIds();
}
