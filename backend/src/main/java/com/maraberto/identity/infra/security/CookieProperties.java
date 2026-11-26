package com.maraberto.identity.infra.security;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Política do cookie de refresh e das origens aceitas nas rotas de sessão.
 *
 * <p>{@code secure=true} é o padrão e o único aceito no perfil {@code prod}. A
 * lista de origens é a allowlist verificada em {@code /refresh} e
 * {@code /logout}: o cookie {@code SameSite=Strict} já barra o navegador
 * comum, mas a checagem explícita cobre clientes que montam a requisição fora
 * do navegador.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "maraberto.security.cookie")
public class CookieProperties {

    /**
     * Envia o cookie apenas por HTTPS. Desligar é só para desenvolvimento em
     * HTTP; o perfil {@code prod} recusa a aplicação nesse estado.
     */
    private boolean secure = true;

    /** Origens que podem chamar {@code /refresh} e {@code /logout}. */
    private List<String> allowedOrigins = List.of(
            "http://localhost",
            "http://127.0.0.1",
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "http://localhost:3000",
            "http://127.0.0.1:3000",
            "https://localhost",
            "capacitor://localhost");
}
