package com.maraberto.beaches.application;

import java.text.Normalizer;
import java.util.Locale;

/** Compara nomes sem depender de acento, caixa ou espaço extra. */
final class NameNormalizer {

    private NameNormalizer() {
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return withoutAccents.toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
    }
}
