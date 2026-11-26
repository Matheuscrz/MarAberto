package com.maraberto.operations.domain;

/**
 * Guarda-vidas tentou encerrar uma sinalização que não lançou e cuja área de
 * banho não corresponde ao seu plantão aberto.
 */
public class FlagOutsideScopeException extends RuntimeException {

    public FlagOutsideScopeException() {
        super("Você só pode encerrar sinalizações lançadas por você ou da praia do seu plantão aberto.");
    }
}
