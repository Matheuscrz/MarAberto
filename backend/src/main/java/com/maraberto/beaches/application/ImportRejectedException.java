package com.maraberto.beaches.application;

/** Arquivo/estado rejeitado antes de qualquer escrita no mapa. */
public class ImportRejectedException extends RuntimeException {

    public ImportRejectedException(String message) {
        super(message);
    }
}
