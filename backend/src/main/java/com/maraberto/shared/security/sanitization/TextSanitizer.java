package com.maraberto.shared.security.sanitization;

import com.maraberto.shared.security.CpfValidator;
import java.net.URI;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

/**
 * Sanitização de entradas textuais vindas da web.
 *
 * <p>Defesa em profundidade: remove markup perigoso com OWASP Java HTML
 * Sanitizer, normaliza Unicode, elimina caracteres de controle e aplica
 * allowlists por tipo. A saída de texto puro ainda deve ser escapada no
 * momento da renderização (React já faz isso); sanitização não substitui
 * output encoding.
 */
public final class TextSanitizer {

    private static final PolicyFactory PLAIN_HTML = new HtmlPolicyBuilder().toFactory();

    private static final PolicyFactory RICH_HTML = new HtmlPolicyBuilder()
            .allowElements("p", "br", "strong", "b", "em", "i", "ul", "ol", "li",
                    "blockquote", "h2", "h3", "h4", "a")
            .allowAttributes("href").onElements("a")
            .allowUrlProtocols("https", "http")
            .requireRelNofollowOnLinks()
            .toFactory();

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z0-9._:@-]{1,100}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]{1,255}$");
    private static final Pattern PHONE = Pattern.compile("^[+0-9()\\-\\s]{7,20}$");
    private static final Pattern YOUTUBE_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x?[0-9A-Fa-f]+);");

    private static final int DEFAULT_MAX_LENGTH = 2000;

    private TextSanitizer() {
    }

    public static String sanitize(String value, SanitizationPolicy policy) {
        return sanitize(value, policy, DEFAULT_MAX_LENGTH);
    }

    public static String sanitize(String value, SanitizationPolicy policy, int maxLength) {
        if (value == null) {
            return null;
        }
        if (maxLength <= 0) {
            throw new IllegalArgumentException("maxLength deve ser positivo");
        }

        return switch (policy) {
            case PLAIN_TEXT -> limit(normalizePlain(value), maxLength);
            case RICH_TEXT -> limit(normalizeRich(RICH_HTML.sanitize(value)), maxLength);
            case IDENTIFIER -> requireMatch(normalizePlain(value), IDENTIFIER,
                    "identificador inválido", maxLength);
            case EMAIL -> requireMatch(
                    normalizePlain(value).toLowerCase(Locale.ROOT), EMAIL, "e-mail inválido", maxLength);
            case PHONE -> requireMatch(normalizePlain(value), PHONE, "telefone inválido", maxLength);
            case CPF -> sanitizeCpf(value);
            case URL -> sanitizeUrl(value, maxLength);
            case YOUTUBE_ID -> requireMatch(normalizePlain(value), YOUTUBE_ID,
                    "ID de vídeo inválido", 11);
        };
    }

    private static String sanitizeCpf(String value) {
        String digits = CpfValidator.normalize(decodeEntities(PLAIN_HTML.sanitize(value)));
        if (!CpfValidator.isValid(digits)) {
            throw new IllegalArgumentException("CPF inválido");
        }
        return digits;
    }

    private static String sanitizeUrl(String value, int maxLength) {
        String candidate = limit(decodeEntities(PLAIN_HTML.sanitize(value)).trim(), maxLength);
        URI uri;
        try {
            uri = URI.create(candidate);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("URL inválida", exception);
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("URL deve usar http ou https");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("URL inválida");
        }
        return uri.toString();
    }

    private static String normalizePlain(String value) {
        String sanitized = decodeEntities(PLAIN_HTML.sanitize(value));
        return Normalizer.normalize(sanitized, Normalizer.Form.NFKC)
                .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String normalizeRich(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "")
                .trim();
    }

    private static String requireMatch(String value, Pattern pattern, String message, int maxLength) {
        limit(value, maxLength);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static String limit(String value, int maxLength) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    "texto excede o tamanho máximo de " + maxLength + " caracteres");
        }
        return value;
    }

    /**
     * O sanitizer devolve entidades HTML escapadas. Para texto puro, decodifica
     * apenas entidades seguras depois de remover o markup. A renderização final
     * continua sendo responsável por escapar a saída.
     */
    private static String decodeEntities(String value) {
        Matcher matcher = NUMERIC_ENTITY.matcher(value);
        StringBuilder decoded = new StringBuilder();
        while (matcher.find()) {
            String code = matcher.group(1);
            int radix = code.startsWith("x") || code.startsWith("X") ? 16 : 10;
            String digits = radix == 16 ? code.substring(1) : code;
            try {
                matcher.appendReplacement(decoded,
                        Matcher.quoteReplacement(new String(Character.toChars(Integer.parseInt(digits, radix)))));
            } catch (IllegalArgumentException exception) {
                matcher.appendReplacement(decoded, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(decoded);

        return decoded.toString()
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
    }
}
