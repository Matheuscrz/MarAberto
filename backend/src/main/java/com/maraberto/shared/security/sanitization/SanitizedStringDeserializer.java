package com.maraberto.shared.security.sanitization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import java.io.IOException;

/**
 * Desserializador Jackson acionado por {@link Sanitized}.
 */
public class SanitizedStringDeserializer extends JsonDeserializer<String>
        implements ContextualDeserializer {

    private final SanitizationPolicy policy;
    private final int maxLength;

    public SanitizedStringDeserializer() {
        this(SanitizationPolicy.PLAIN_TEXT, 2000);
    }

    private SanitizedStringDeserializer(SanitizationPolicy policy, int maxLength) {
        this.policy = policy;
        this.maxLength = maxLength;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext context, BeanProperty property) {
        Sanitized annotation = property == null ? null : property.getAnnotation(Sanitized.class);
        if (annotation == null && property != null) {
            annotation = property.getContextAnnotation(Sanitized.class);
        }
        if (annotation == null) {
            return this;
        }
        return new SanitizedStringDeserializer(annotation.value(), annotation.maxLength());
    }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String value = parser.getValueAsString();
        if (value == null) {
            return null;
        }
        try {
            return TextSanitizer.sanitize(value, policy, maxLength);
        } catch (IllegalArgumentException exception) {
            // Falha fechada: valores fora da política (identificador, e-mail,
            // CPF, URL etc.) viram string vazia para que o Bean Validation
            // produza um erro de campo (RFC 9457) em vez de um 400 genérico.
            // Texto livre acima do limite continua sendo rejeitado.
            if (policy == SanitizationPolicy.PLAIN_TEXT
                    || policy == SanitizationPolicy.RICH_TEXT) {
                throw JsonMappingException.from(parser, exception.getMessage(), exception);
            }
            return "";
        }
    }
}
