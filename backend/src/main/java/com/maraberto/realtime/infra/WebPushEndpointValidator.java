package com.maraberto.realtime.infra;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Valida URLs de endpoint Web Push contra SSRF (F-05).
 *
 * <p>Exige esquema {@code https}, porta padrão 443, host público FQDN (sem
 * literais IP, sem nomes internos single-label e sem sufixos locais) e, quando
 * o DNS resolve, bloqueia endereços de loopback, link-local (incluindo metadata
 * de cloud {@code 169.254.169.254}), redes privadas RFC 1918/4193, CGNAT e
 * multicast.
 */
@Component
public class WebPushEndpointValidator {

    public static final int MAX_ENDPOINT_LENGTH = 2048;

    private static final Pattern IPV4_LITERAL = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    @FunctionalInterface
    public interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final HostResolver hostResolver;

    public WebPushEndpointValidator() {
        this(InetAddress::getAllByName);
    }

    public WebPushEndpointValidator(HostResolver hostResolver) {
        this.hostResolver = hostResolver;
    }

    /** Valida o endpoint ao cadastrar a inscrição. */
    public String validateForRegistration(String rawEndpoint) {
        return validate(rawEndpoint, false);
    }

    /** Valida defensivamente o endpoint imediatamente antes do envio HTTP. */
    public String validateForSend(String rawEndpoint) {
        return validate(rawEndpoint, true);
    }

    private String validate(String rawEndpoint, boolean requireDnsResolution) {
        if (rawEndpoint == null || rawEndpoint.isBlank()) {
            throw new IllegalArgumentException("Endpoint Web Push obrigatório.");
        }
        String trimmed = rawEndpoint.trim();
        if (trimmed.length() > MAX_ENDPOINT_LENGTH) {
            throw new IllegalArgumentException("Endpoint Web Push excede o tamanho máximo permitido.");
        }
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Endpoint Web Push com URL inválida.");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Endpoint Web Push deve usar esquema https.");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Endpoint Web Push não pode conter credenciais na URL.");
        }
        int port = uri.getPort();
        if (port != -1 && port != 443) {
            throw new IllegalArgumentException("Endpoint Web Push deve usar a porta padrão 443.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Endpoint Web Push sem host válido.");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.endsWith(".")) {
            normalizedHost = normalizedHost.substring(0, normalizedHost.length() - 1);
        }
        if (isDisallowedHostName(normalizedHost)) {
            throw new IllegalArgumentException("Host do endpoint Web Push não permitido.");
        }
        if (isIpLiteral(normalizedHost)) {
            throw new IllegalArgumentException("Endpoint Web Push não pode usar endereço IP literal.");
        }
        try {
            InetAddress[] addresses = hostResolver.resolve(normalizedHost);
            if (addresses == null || addresses.length == 0) {
                throw new IllegalArgumentException("Host do endpoint Web Push não resolvido.");
            }
            for (InetAddress address : addresses) {
                if (isPrivateOrRestricted(address)) {
                    throw new IllegalArgumentException(
                            "Endpoint Web Push resolve para endereço de rede privada ou restrita.");
                }
            }
        } catch (UnknownHostException exception) {
            if (requireDnsResolution) {
                throw new IllegalArgumentException("Host do endpoint Web Push não resolvido.");
            }
        }
        return trimmed;
    }

    private static boolean isDisallowedHostName(String host) {
        if (!host.contains(".")) {
            return true;
        }
        return host.equals("localhost")
                || host.endsWith(".localhost")
                || host.endsWith(".local")
                || host.endsWith(".internal")
                || host.endsWith(".lan")
                || host.endsWith(".home")
                || host.endsWith(".arpa");
    }

    private static boolean isIpLiteral(String host) {
        if (host.startsWith("[") || host.contains(":")) {
            return true;
        }
        return IPV4_LITERAL.matcher(host).matches();
    }

    private static boolean isPrivateOrRestricted(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            // 0.0.0.0/8 (rede atual), 100.64.0.0/10 (CGNAT), 192.0.0.0/24, 198.18.0.0/15 (benchmark)
            if (first == 0) {
                return true;
            }
            if (first == 100 && (second & 0xC0) == 64) {
                return true;
            }
            if (first == 198 && (second == 18 || second == 19)) {
                return true;
            }
        } else if (bytes.length == 16) {
            // fc00::/7 (IPv6 Unique Local Address)
            if ((bytes[0] & 0xFE) == 0xFC) {
                return true;
            }
        }
        return false;
    }
}
