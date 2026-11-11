package com.maraberto.operations.api;

/**
 * Tipo de estrutura em que o operador pode abrir plantão.
 *
 * <p>O posto fixo tem uma posição estrutural que não muda; a base móvel é o
 * ponto de referência enquanto o veículo se desloca.
 */
public enum ShiftTargetKind {
    POST,
    MOBILE_BASE
}
