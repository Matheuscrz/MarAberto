package com.maraberto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.maraberto.identity.api.AuthCookie;
import com.maraberto.identity.api.IdentityApiExceptionHandler;
import com.maraberto.identity.api.dto.RegisterOperationalUserCommand;
import com.maraberto.identity.application.AuthenticationService;
import com.maraberto.identity.domain.OperationalUser;
import com.maraberto.identity.domain.Role;
import com.maraberto.identity.infra.AuthSessionRepository;
import com.maraberto.identity.infra.OperationalUserRepository;
import com.maraberto.shared.security.web.ApiAuthenticationEntryPoint;
import com.jayway.jsonpath.JsonPath;
import com.maraberto.support.PostgisIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Login único, rotação de refresh, detecção de reuso e autorização por papel.
 *
 * <p>Exercita o caminho real (filtro JWT + cookie + Argon2 + Postgres), não
 * mocks: é a integração que o frontend consome.
 */
@SpringBootTest
@AutoConfigureMockMvc
class IdentityAuthIntegrationTests extends PostgisIntegrationTest {

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
    void loginReturnsAccessTokenUserAndHardenedRefreshCookie() throws Exception {
        createUser("operador@maraberto.dev", "BM-10492", "11144477735", Role.LIFEGUARD, true);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"identifier":"operador@maraberto.dev","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.name").value("Operador Teste"))
                .andExpect(jsonPath("$.user.role").value("LIFEGUARD"))
                .andExpect(jsonPath("$.user.badgeNumber").value("BM-10492"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .contains(AuthCookie.NAME + "=")
                .contains("Path=" + AuthCookie.PATH)
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict");
        assertThat(refreshTokenOf(setCookie)).isNotBlank();
    }

    @Test
    void loginAcceptsCpfAndBadgeNumber() throws Exception {
        createUser("operador@maraberto.dev", "BM-10492", "11144477735", Role.LIFEGUARD, true);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"identifier":"111.444.777-35","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("LIFEGUARD"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"identifier":"bm-10492","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("operador@maraberto.dev"));
    }

    @Test
    void wrongPasswordAndUnknownIdentifierShareTheSameProblem() throws Exception {
        createUser("operador@maraberto.dev", "BM-10492", "11144477735", Role.LIFEGUARD, true);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"identifier":"operador@maraberto.dev","password":"senhaerrada"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(IdentityApiExceptionHandler.INVALID_CREDENTIALS));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"identifier":"ninguem@maraberto.dev","password":"senhaerrada"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value(IdentityApiExceptionHandler.INVALID_CREDENTIALS));
    }

    @Test
    void inactiveAccountIsForbiddenOnlyAfterCorrectPassword() throws Exception {
        createUser("operador@maraberto.dev", "BM-10492", "11144477735", Role.LIFEGUARD, false);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"identifier":"operador@maraberto.dev","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(IdentityApiExceptionHandler.ACCOUNT_INACTIVE));
    }

    @Test
    void refreshRotatesTheTokenAndToleratesAMultiTabRace() throws Exception {
        createUser("operador@maraberto.dev", "BM-10492", "11144477735", Role.LIFEGUARD, true);
        String originalToken = loginAndGetRefreshToken("operador@maraberto.dev");

        MvcResult rotated = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(AuthCookie.NAME, originalToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String rotatedToken = refreshTokenOf(
                rotated.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        assertThat(rotatedToken).isNotEqualTo(originalToken);

        // Duas abas renovando quase juntas: dentro da janela de tolerância o
        // reuso é benigno e emite um irmão na mesma família.
        MvcResult sibling = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(AuthCookie.NAME, originalToken)))
                .andExpect(status().isOk())
                .andReturn();

        String siblingToken = refreshTokenOf(
                sibling.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        assertThat(siblingToken).isNotEqualTo(originalToken).isNotEqualTo(rotatedToken);

        // A família continua válida para o token mais recente.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(AuthCookie.NAME, siblingToken)))
                .andExpect(status().isOk());
    }

    @Test
    void logoutRevokesTheFamilyAndClearsTheCookie() throws Exception {
        createUser("operador@maraberto.dev", "BM-10492", "11144477735", Role.LIFEGUARD, true);
        String refreshToken = loginAndGetRefreshToken("operador@maraberto.dev");

        MvcResult logout = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie(AuthCookie.NAME, refreshToken)))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(logout.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains(AuthCookie.NAME + "=")
                .contains("Max-Age=0");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(AuthCookie.NAME, refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meRequiresAValidAccessToken() throws Exception {
        createUser("coordenador@maraberto.dev", "CBM-2041", "52998224725", Role.COORDINATOR, true);
        String accessToken = loginAndGetAccessToken("coordenador@maraberto.dev");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("COORDINATOR"))
                .andExpect(jsonPath("$.badgeNumber").value("CBM-2041"));

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(ApiAuthenticationEntryPoint.PROBLEM_TYPE));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRejectsMalformedPayloadAsProblemJson() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"identifier":"","password":"123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(IdentityApiExceptionHandler.INVALID_REQUEST))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void identityRoutesKeepThePublicOnesOpen() throws Exception {
        // Sem token, a rota pública do SOS continua acessível (validação 400, não 401).
        mockMvc.perform(post("/api/v1/emergency-calls")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private void createUser(
            String email,
            String badgeNumber,
            String cpf,
            Role role,
            boolean active) {
        UUID id = authenticationService.registerOperational(new RegisterOperationalUserCommand(
                "Operador Teste", badgeNumber, email, cpf, PASSWORD, role));
        if (!active) {
            OperationalUser user = users.findById(id).orElseThrow();
            user.deactivate();
            users.save(user);
        }
    }

    private String loginAndGetRefreshToken(String identifier) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(identifier, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return refreshTokenOf(result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
    }

    private String loginAndGetAccessToken(String identifier) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(identifier, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    /** Extrai só {@code nome=valor} do {@code Set-Cookie}, sem os atributos. */
    private static String refreshTokenOf(String setCookie) {
        assertThat(setCookie).isNotNull();
        String pair = setCookie.split(";", 2)[0];
        return pair.substring(pair.indexOf('=') + 1);
    }
}
