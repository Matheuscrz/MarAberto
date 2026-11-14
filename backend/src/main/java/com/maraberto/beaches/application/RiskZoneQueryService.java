package com.maraberto.beaches.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.maraberto.beaches.infra.PublicRiskZoneRepository;
import org.springframework.stereotype.Service;

/**
 * Consulta pública das zonas de risco visíveis.
 *
 * <p>A validade é aplicada no banco: zona permanente nunca expira, zona
 * semipermanente expira em {@code valid_until}. O filtro por retângulo é
 * opcional; sem ele a resposta traz todas as zonas visíveis.
 */
@Service
public class RiskZoneQueryService {

    private final PublicRiskZoneRepository repository;

    public RiskZoneQueryService(PublicRiskZoneRepository repository) {
        this.repository = repository;
    }

    public JsonNode visible(Double west, Double south, Double east, Double north) {
        return repository.findVisible(bbox(west, south, east, north));
    }

    /**
     * O retângulo é tudo ou nada: meia caixa passaria despercebida e devolveria
     * o estado inteiro como se fosse a área pedida.
     */
    private static double[] bbox(Double west, Double south, Double east, Double north) {
        boolean any = west != null || south != null || east != null || north != null;
        if (!any) {
            return null;
        }
        if (west == null || south == null || east == null || north == null) {
            throw new InvalidLocationException(
                    "Informe os quatro cantos do retângulo (west, south, east, north) ou nenhum");
        }
        if (west >= east || south >= north) {
            throw new InvalidLocationException(
                    "Retângulo inválido: west deve ser menor que east e south menor que north");
        }
        return new double[] {west, south, east, north};
    }
}
