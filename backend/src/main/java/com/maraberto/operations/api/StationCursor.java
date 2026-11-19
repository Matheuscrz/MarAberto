package com.maraberto.operations.api;

import com.maraberto.operations.domain.StationKind;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Cursor opaco da lista de estruturas.
 *
 * <p>Guarda a chave de ordenação {@code (kind, name, id)} da última linha
 * entregue. O cliente não precisa conhecer o formato: recebe
 * {@code nextCursor} e devolve o mesmo valor. Nome vai em
 * {@link URLEncoder} para que separadores e acentos sobrevivam ao Base64 sem
 * ambiguidade.
 */
public record StationCursor(StationKind kind, String name, UUID id) {

    private static final String SEPARATOR = "|";

    public String encode() {
        String raw = String.join(SEPARATOR,
                kind.name(),
                URLEncoder.encode(name, StandardCharsets.UTF_8),
                id.toString());
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static StationCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(
                    Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split(
                    java.util.regex.Pattern.quote(SEPARATOR), -1);
            if (parts.length != 3) {
                throw new IllegalArgumentException(
                        "Cursor inválido: devolva o nextCursor recebido.");
            }
            return new StationCursor(
                    StationKind.valueOf(parts[0]),
                    URLDecoder.decode(parts[1], StandardCharsets.UTF_8),
                    UUID.fromString(parts[2]));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Cursor inválido: devolva o nextCursor recebido.");
        }
    }
}
