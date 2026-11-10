package com.maraberto.identity;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.maraberto.identity.api.AuthCookie;
import com.maraberto.identity.api.IdentityApiExceptionHandler;
import com.maraberto.identity.api.dto.RegisterOperationalUserCommand;
import com.maraberto.identity.application.AuthenticationService;
import com.maraberto.identity.domain.Role;
import com.maraberto.identity.infra.AuthSessionRepository;
import com.maraberto.identity.infra.OperationalUserRepository;
import com.maraberto.support.PostgisIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Reuso de refresh token fora da janela de tolerância.
 *
 * <p>A tolerância é zerada aqui para exercitar a detecção de reuso sem esperar
 * 10 segundos: apresentar um token já rotacionado revoga a família inteira,
 * inclusive o sucessor legítimo.
 */
@SpringBootTest(properties = "maraberto.security.jwt.refresh-reuse-tolerance=PT0S")
@AutoConfigureMockMvc
class RefreshTokenReuseTests extends PostgisIntegrationTest {

    private static final String PASSWORD = "senha1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private OperationalUserRepository users;

    @Autowired
    private AuthSessionRepository sessions;


    @BeforeEach
    void cleanDatabase() {
        sessions.deleteAll();
        users.deleteAll();
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeFamily() throws Exception {
        authenticationService.registerOperational(new RegisterOperationalUserCommand(
                "Operador Teste",
                "BM-10492",
                "operador@maraberto.dev",
                "11144477735",
                PASSWORD,
                Role.LIFEGUARD));

        String originalToken = loginAndGetRefreshToken();

        MvcResult rotated = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(AuthCookie.NAME, originalToken)))
                .andExpect(status().isOk())
                .andReturn();
        String rotatedToken = refreshTokenOf(
                rotated.getResponse().getHeader(HttpHeaders.SET_COOKIE));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(AuthCookie.NAME, originalToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value(IdentityApiExceptionHandler.INVALID_REFRESH_TOKEN));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(AuthCookie.NAME, rotatedToken)))
                .andExpect(status().isUnauthorized());
    }

    private String loginAndGetRefreshToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"identifier":"operador@maraberto.dev","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String pair = setCookie.split(";", 2)[0];
        return pair.substring(pair.indexOf('=') + 1);
    }

    private static String refreshTokenOf(String setCookie) {
        String pair = setCookie.split(";", 2)[0];
        return pair.substring(pair.indexOf('=') + 1);
    }
}
