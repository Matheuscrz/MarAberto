package com.maraberto.beaches.application;

import java.util.List;

/**
 * Linha do relatório de importação.
 *
 * @param changedFields campos realmente alterados (vazio em criado/inalterado)
 */
public record FeatureChange(
        String externalId,
        String areaExternalId,
        String area,
        String sector,
        String municipality,
        FeatureAction action,
        List<String> changedFields,
        String message) {

    public static FeatureChange of(
            String externalId,
            String areaExternalId,
            String area,
            String sector,
            String municipality,
            FeatureAction action,
            List<String> changedFields) {
        return new FeatureChange(
                externalId, areaExternalId, area, sector, municipality,
                action, List.copyOf(changedFields), null);
    }

    public static FeatureChange rejected(
            String externalId, String area, String sector, String municipality, String message) {
        return new FeatureChange(
                externalId, null, area, sector, municipality, FeatureAction.REJECTED,
                List.of(), message);
    }
}
