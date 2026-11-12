package com.maraberto.operations.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Encerra plantões sem sinal de vida.
 *
 * <p>Fechar o app à força, desligar o aparelho ou perder a rede não geram
 * nenhum aviso ao servidor. Sem este job, o operador continuaria "de plantão"
 * indefinidamente e entraria na seleção espacial do SOS, recebendo chamados que
 * não vai atender (ADR-018).
 *
 * <p>O relógio é o {@code last_seen_at} do plantão, renovado pelo console ao
 * abrir, ao atualizar a posição e ao voltar ao primeiro plano. O horário é
 * {@code maraberto.dispatch.shift-idle-timeout} (padrão 15 minutos): curto o
 * bastante para o turno refletir a realidade, longo o bastante para não derrubar
 * quem apenas perdeu sinal por alguns minutos.
 */
@Component
public class ShiftIdleExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ShiftIdleExpiryJob.class);

    private final ShiftService shiftService;

    public ShiftIdleExpiryJob(ShiftService shiftService) {
        this.shiftService = shiftService;
    }

    @Scheduled(
            fixedDelayString = "${maraberto.dispatch.shift-sweep-interval:PT10M}",
            initialDelayString = "${maraberto.dispatch.shift-sweep-initial-delay:PT2M}")
    public void closeIdleShifts() {
        try {
            shiftService.closeIdleShifts();
        } catch (RuntimeException cause) {
            // O job é repetido; um erro de banco não pode parar o agendador.
            log.warn("Falha ao encerrar plantões inativos: {}", cause.getClass().getSimpleName());
        }
    }
}
