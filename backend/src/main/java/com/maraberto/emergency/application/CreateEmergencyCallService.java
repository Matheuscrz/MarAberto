package com.maraberto.emergency.application;

import com.maraberto.emergency.domain.EmergencyCall;
import com.maraberto.emergency.infra.EmergencyCallRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Criação idempotente do SOS.
 *
 * <p>A RN05/ADR-018 exige que o mesmo {@code clientRequestId} nunca gere dois
 * chamados. A busca antes de criar cobre o caso comum; a corrida entre dois
 * pedidos simultâneos é decidida pela constraint única de
 * {@code emergency_calls.client_request_id}. Quando a violação acontece, o
 * chamado que venceu é devolvido em vez de virar erro 500 para o banhista.
 *
 * <p>Não é transacional de propósito: a gravação acontece em
 * {@link EmergencyCallRegistrar}, e a leitura de recuperação precisa rodar depois
 * do rollback da tentativa perdedora.
 */
@Service
public class CreateEmergencyCallService {

    private final EmergencyCallRepository callRepository;
    private final EmergencyCallRegistrar registrar;
    private final SosRateLimiter rateLimiter;

    public CreateEmergencyCallService(
            EmergencyCallRepository callRepository,
            EmergencyCallRegistrar registrar,
            SosRateLimiter rateLimiter) {
        this.callRepository = callRepository;
        this.registrar = registrar;
        this.rateLimiter = rateLimiter;
    }

    public EmergencyCall create(CreateEmergencyCallCommand command) {
        EmergencyCall existing = callRepository
                .findByClientRequestId(command.clientRequestId())
                .orElse(null);
        if (existing != null) {
            // Reenvio do mesmo pedido não é um chamado novo: não consome ficha
            // do limite (uma rede instável não pode bloquear a vítima).
            return existing;
        }
        rateLimiter.check(command.clientAddress());
        try {
            return registrar.register(command);
        } catch (DataIntegrityViolationException exception) {
            return callRepository.findByClientRequestId(command.clientRequestId())
                    .orElseThrow(() -> exception);
        }
    }

    @Schema(name = "CreateEmergencyCallCommand", description = "Comando interno para criação idempotente de um SOS")
    public record CreateEmergencyCallCommand(
            String clientRequestId,
            double latitude,
            double longitude,
            Double accuracyMeters,
            String clientAddress) {
    }
}
