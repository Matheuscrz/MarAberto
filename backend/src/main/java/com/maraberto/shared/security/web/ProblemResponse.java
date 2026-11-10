package com.maraberto.shared.security.web;

import java.util.List;

/**
 * Corpo mínimo de erro RFC 9457 usado pelos filtros de segurança, que escrevem
 * a resposta antes de a requisição chegar ao MVC.
 */
public record ProblemResponse(
        String type,
        int status,
        String title,
        String detail,
        String instance,
        List<FieldError> errors) {

    public ProblemResponse(String type, int status, String title, String detail, String instance) {
        this(type, status, title, detail, instance, null);
    }

    public record FieldError(String field, String message) {
    }
}
