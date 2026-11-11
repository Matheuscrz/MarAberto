package com.maraberto.emergency.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Serializa eventos da outbox do SOS. Isolado para que a criação do chamado e o
 * publicador compartilhem a mesma representação.
 */
@Component
public class EmergencyOutboxSerializer {

    private final ObjectMapper objectMapper;

    public EmergencyOutboxSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Não foi possível serializar o evento da outbox do SOS", exception);
        }
    }

    public <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Evento da outbox do SOS ilegível", exception);
        }
    }
}
