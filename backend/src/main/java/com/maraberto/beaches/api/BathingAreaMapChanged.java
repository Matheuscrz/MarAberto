package com.maraberto.beaches.api;

/** Um lote de importação alterou o mapa de uma UF (invalida a projeção pública). */
public record BathingAreaMapChanged(String stateCode) {
}
