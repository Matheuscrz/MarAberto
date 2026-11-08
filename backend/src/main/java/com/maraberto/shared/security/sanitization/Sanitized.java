package com.maraberto.shared.security.sanitization;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sanitiza automaticamente um campo {@link String} recebido via JSON.
 *
 * <p>Use em todo DTO de request que aceite texto livre. O valor é sanitizado
 * antes de chegar ao caso de uso; entradas que não passam na política
 * (identificador, e-mail, CPF, URL etc.) resultam em HTTP 400.
 *
 * <pre>{@code
 * @Sanitized(SanitizationPolicy.PLAIN_TEXT, maxLength = 200)
 * String name
 * }</pre>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT,
        ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonDeserialize(using = SanitizedStringDeserializer.class)
public @interface Sanitized {

    SanitizationPolicy value() default SanitizationPolicy.PLAIN_TEXT;

    int maxLength() default 2000;
}
