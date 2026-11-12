package com.maraberto.beaches.application;

/** Modo de reimportação (ADR-016). */
public enum ImportMode {
    /** Cria e atualiza; nunca remove nem desativa. */
    UPSERT,
    /** Cria/atualiza e marca como inativa a feição que sumiu do arquivo. */
    SYNC
}
