package com.maraberto.beaches.domain;

/**
 * Persistência temporal de uma zona de risco.
 *
 * <p>O mesmo tipo de risco pode ter comportamentos diferentes: uma corrente de
 * retorno ao lado de um píer é estrutural e permanente, enquanto a que se forma
 * entre bancos de areia dura dias ou semanas, e a de praia aberta pode mudar em
 * horas. A persistência define qual desses casos a zona representa.
 */
public enum RiskPersistence {

    /**
     * Risco estrutural que não muda de lugar: píeres, molhes, quebra-mares,
     * costões rochosos e foz de rio/canal. Não possui expiração.
     */
    PERMANENT,

    /**
     * Risco topográfico que permanece no mesmo ponto por dias, semanas ou meses
     * (ex.: canal entre bancos de areia). Exige validade e revalidação após
     * ressacas ou mudanças no relevo.
     */
    SEMI_PERMANENT,

    /**
     * Risco migratório/efêmero, que surge, se desloca e desaparece em horas ou
     * minutos (ex.: corrente de retorno em praia aberta). Não deve ser cadastrado
     * como zona permanente: pertence à demarcação dinâmica de correntes.
     */
    EPHEMERAL
}
