package com.maraberto.audit.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Cadeia de hashes da trilha.
 *
 * <p>Cada linha calcula {@code sha256(prev_hash || campos canônicos)}. Os campos
 * entram em uma ordem fixa e o detalhe JSON é serializado com as chaves
 * ordenadas, para que a mesma linha produza o mesmo hash na escrita e na
 * conferência — inclusive depois de o PostgreSQL normalizar o {@code jsonb}.
 *
 * <p>É uma prova de integridade, não uma assinatura: quem tem acesso de escrita
 * ao banco pode reconstruir a cadeia. O objetivo é detectar alteração casual ou
 * de aplicação, não substituir backup e controle de acesso.
 */
public final class AuditHashChain {

    /** Predecessor da primeira linha: 64 zeros. */
    public static final String GENESIS = "0".repeat(64);

    private AuditHashChain() {
    }

    public static String canonical(
            Instant occurredAt,
            UUID actorId,
            String actorRole,
            String action,
            String entityType,
            String entityId,
            String traceId,
            String ipBidx,
            String detailJson) {
        return "v1\n"
                + occurredAt.toString() + '\n'
                + text(actorId) + '\n'
                + text(actorRole) + '\n'
                + text(action) + '\n'
                + text(entityType) + '\n'
                + text(entityId) + '\n'
                + text(traceId) + '\n'
                + text(ipBidx) + '\n'
                + text(detailJson);
    }

    public static String hash(String prevHash, String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((prevHash == null ? GENESIS : prevHash)
                    .getBytes(StandardCharsets.UTF_8));
            digest.update(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }

    /** JSON do detalhe com chaves ordenadas em todos os níveis. */
    public static String detailJson(Map<String, Object> detail, ObjectMapper mapper) {
        Object canonical = canonicalize(detail == null ? Map.of() : detail);
        try {
            return mapper.writeValueAsString(canonical);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("detalhe de auditoria não serializa", exception);
        }
    }

    private static Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), canonicalize(item)));
            return sorted;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(AuditHashChain::canonicalize).toList();
        }
        return value;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
