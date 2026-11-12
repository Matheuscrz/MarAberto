package com.maraberto.beaches.application;

import java.util.Map;
import org.locationtech.jts.geom.MultiPolygon;

/**
 * Feição lida do KML: um {@code Placemark} é um setor, não uma área.
 *
 * @param id        identificador estável na origem (obrigatório)
 * @param praia     nome da área de banho na origem
 * @param setor     nome do setor; {@code null} quando a origem manda {@code "-"}
 * @param municipio município da área
 * @param uf        UF declarada na feição; pode ser nula
 * @param geometry  geometria 2D em SRID 4326
 * @param metadata  {@code ExtendedData} bruto (proveniência, não estado vivo)
 */
public record ImportedFeature(
        String id,
        String praia,
        String setor,
        String municipio,
        String uf,
        MultiPolygon geometry,
        Map<String, String> metadata) {
}
