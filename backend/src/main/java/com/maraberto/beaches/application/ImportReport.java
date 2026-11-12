package com.maraberto.beaches.application;

import java.util.List;

/**
 * Relatório de uma execução de importação.
 *
 * @param dryRun  {@code true} quando nada foi escrito no mapa
 * @param applied {@code true} quando o lote foi efetivamente aplicado
 */
public record ImportReport(
        String sourceFile,
        String sourceVersion,
        String state,
        ImportMode mode,
        boolean dryRun,
        boolean applied,
        int created,
        int updated,
        int unchanged,
        int missing,
        int rejected,
        List<FeatureChange> changes) {
}
