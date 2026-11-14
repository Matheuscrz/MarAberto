package com.maraberto.media.domain;

/**
 * O arquivo enviado não é uma imagem que o servidor consiga abrir (ou é grande
 * demais para ser processado com segurança).
 */
public class InvalidImageException extends RuntimeException {

    public InvalidImageException(String message) {
        super(message);
    }

    public InvalidImageException() {
        this("Envie uma foto JPEG ou PNG válida.");
    }
}
