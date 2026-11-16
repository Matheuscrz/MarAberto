package com.maraberto.identity.infra.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Configuração de produção que o boot não pode perder em silêncio.
 *
 * <p>O {@code SecurityConfig} libera {@code /swagger-ui/**} e
 * {@code /v3/api-docs/**} sem autenticação — é o comportamento desejado em
 * desenvolvimento, mas em produção entrega o contrato inteiro da API a quem
 * alcançar o host. Quem desliga é o springdoc, em {@code application-prod.yml}.
 */
class ProductionDocsConfigurationTests {

    @Test
    void productionProfileDisablesSwaggerAndApiDocs() throws IOException {
        assertThat(loadProductionProperties())
                .containsEntry("springdoc.api-docs.enabled", false)
                .containsEntry("springdoc.swagger-ui.enabled", false);
    }

    @Test
    void productionProfileTrustsForwardedHeadersOnlyFromProxy() throws IOException {
        // `framework` aceita X-Forwarded-* de qualquer cliente; só `native`
        // (ou `none`) confia no cabeçalho atrás de proxy interno, que é a
        // premissa do rate limit e do lockout por IP.
        assertThat(loadProductionProperties())
                .containsEntry("server.forward-headers-strategy", "native");
    }

    private static Map<String, Object> loadProductionProperties() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application-prod", new ClassPathResource("application-prod.yml"));
        assertThat(sources)
                .as("application-prod.yml precisa existir no classpath")
                .isNotEmpty();
        return ((Map<?, ?>) sources.get(0).getSource()).entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> String.valueOf(entry.getKey()),
                        entry -> unwrap(entry.getValue())));
    }

    /** O loader embrulha cada valor com a origem (linha do arquivo). */
    private static Object unwrap(Object value) {
        return value instanceof OriginTrackedValue tracked ? tracked.getValue() : value;
    }
}
