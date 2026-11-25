package com.maraberto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.maraberto.identity.api.AuthCookie;
import com.maraberto.identity.api.IdentityApiExceptionHandler;
import com.maraberto.identity.api.dto.RegisterOperationalUserCommand;
import com.maraberto.identity.application.AuthenticationService;
import com.maraberto.identity.domain.Role;
import com.maraberto.identity.infra.AuthSessionRepository;
import com.maraberto.identity.infra.OperationalUserRepository;
import com.maraberto.support.PostgisIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Administração de usuários e trilha de auditoria exercitadas de ponta a ponta
 * (RF20/RNF06, ADR-027): cadastro com senha temporária, bloqueio até a troca,
 * reset, desativação e consulta da trilha.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminUserIntegrationTests extends PostgisIntegrationTest {

    private static final String ADMIN_PASSWORD = "senha1234";
    private static final String NEW_PASSWORD = "Definitiva123";

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
    void createdUserGetsTemporaryPasswordAndIsBlockedUntilTheChange() throws Exception {
        Session admin = adminSession("admin-lista@teste.dev");

        String email = uniqueEmail();
        MvcResult created = mockMvc.perform(post("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Sd. Almeida","badgeNumber":"BM-90001",
                                 "email":"%s","cpf":"%s","role":"LIFEGUARD"}
                                """.formatted(email, validCpf())))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andExpect(jsonPath("$.user.role").value("LIFEGUARD"))
                .andReturn();

        String temporaryPassword = JsonPath.read(
                created.getResponse().getContentAsString(), "$.temporaryPassword");
        String userId = JsonPath.read(created.getResponse().getContentAsString(), "$.user.id");

        MvcResult login = login(email, temporaryPassword);
        String accessToken = JsonPath.read(
                login.getResponse().getContentAsString(), "$.accessToken");
        assertThat((Boolean) JsonPath.read(
                login.getResponse().getContentAsString(), "$.user.mustChangePassword")).isTrue();

        // A rota de negócio é bloqueada antes do MVC.
        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type")
                        .value(IdentityApiExceptionHandler.PASSWORD_CHANGE_REQUIRED));

        mockMvc.perform(post("/api/v1/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"%s"}
                                """.formatted(temporaryPassword, NEW_PASSWORD)))
                .andExpect(status().isNoContent());

        // A temporária não entra mais; a definitiva entra sem marca de troca.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(email, temporaryPassword)))
                .andExpect(status().isUnauthorized());

        MvcResult second = login(email, NEW_PASSWORD);
        assertThat((Boolean) JsonPath.read(
                second.getResponse().getContentAsString(), "$.user.mustChangePassword")).isFalse();

        // Toda a jornada ficou na trilha.
        mockMvc.perform(get("/api/v1/admin/audit")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("action", "USER_CREATED")
                        .param("entityId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("USER_CREATED"))
                .andExpect(jsonPath("$.items[0].actorRole").value("ADMIN"));

        mockMvc.perform(get("/api/v1/admin/audit")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("action", "PASSWORD_CHANGED")
                        .param("entityId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("PASSWORD_CHANGED"));
    }

    @Test
    void resetRevokesSessionsAndIssuesANewTemporaryPassword() throws Exception {
        Session admin = adminSession("admin-reset@teste.dev");
        String email = uniqueEmail();
        String initialPassword = "Inicial12345";
        UUID userId = authenticationService.registerOperational(
                new RegisterOperationalUserCommand(
                        "Operador Reset", "T-RESET", email, validCpf(), initialPassword,
                        Role.LIFEGUARD));
        MvcResult login = login(email, initialPassword);
        String refreshToken = refreshTokenOf(
                login.getResponse().getHeader(HttpHeaders.SET_COOKIE));

        MvcResult reset = mockMvc.perform(
                        post("/api/v1/admin/users/{id}/password-reset", userId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andReturn();
        String temporaryPassword = JsonPath.read(
                reset.getResponse().getContentAsString(), "$.temporaryPassword");

        // A sessão anterior morreu junto com a senha antiga.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(AuthCookie.NAME, refreshToken)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(email, initialPassword)))
                .andExpect(status().isUnauthorized());

        login(email, temporaryPassword);
    }

    @Test
    void deactivatingBlocksLoginAndInvalidatesTheAccessToken() throws Exception {
        Session admin = adminSession("admin-desativa@teste.dev");
        String email = uniqueEmail();
        String password = "Inicial12345";
        UUID userId = authenticationService.registerOperational(
                new RegisterOperationalUserCommand(
                        "Operador Desativado", "T-OFF", email, validCpf(), password,
                        Role.LIFEGUARD));
        MvcResult login = login(email, password);
        String accessToken = JsonPath.read(
                login.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(patch("/api/v1/admin/users/{id}", userId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"active":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(IdentityApiExceptionHandler.ACCOUNT_INACTIVE));

        // Reativar volta a permitir o login (não existe exclusão).
        mockMvc.perform(patch("/api/v1/admin/users/{id}", userId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"active":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        login(email, password);
    }

    @Test
    void adminCannotRemoveOwnTiAccess() throws Exception {
        Session admin = adminSession("admin-auto@teste.dev");

        mockMvc.perform(patch("/api/v1/admin/users/{id}", admin.userId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"LIFEGUARD"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type")
                        .value(IdentityApiExceptionHandler.SELF_ADMIN_PROTECTION));
    }

    @Test
    void listPagesByCursorAndFiltersByRole() throws Exception {
        Session admin = adminSession("admin-pagina@teste.dev");
        createUser(admin, uniqueEmail(), Role.LIFEGUARD);
        createUser(admin, uniqueEmail(), Role.LIFEGUARD);
        createUser(admin, uniqueEmail(), Role.COORDINATOR);

        MvcResult first = mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("role", "LIFEGUARD")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].role").value("LIFEGUARD"))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn();
        String cursor = JsonPath.read(first.getResponse().getContentAsString(), "$.nextCursor");
        assertThat(cursor).isNotBlank();

        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .param("role", "LIFEGUARD")
                        .param("limit", "1")
                        .param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].role").value("LIFEGUARD"));
    }

    @Test
    void nonAdminCannotReadUsersNorTheTrail() throws Exception {
        Session admin = adminSession("admin-permissao@teste.dev");
        Session coordinator = sessionFor(Role.COORDINATOR, "coord-permissao@teste.dev");

        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(coordinator)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/audit")
                        .header(HttpHeaders.AUTHORIZATION, bearer(coordinator)))
                .andExpect(status().isForbidden());

        // A trilha continua íntegra para a TI.
        mockMvc.perform(get("/api/v1/admin/audit/integrity")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    private Session adminSession(String email) throws Exception {
        return sessionFor(Role.ADMIN, email);
    }

    private Session sessionFor(Role role, String email) throws Exception {
        String password = ADMIN_PASSWORD;
        UUID userId = authenticationService.registerOperational(
                new RegisterOperationalUserCommand(
                        "Operador " + role.name(),
                        "T-" + UUID.randomUUID().toString().substring(0, 8),
                        email, validCpf(), password, role));
        MvcResult login = login(email, password);
        String accessToken = JsonPath.read(
                login.getResponse().getContentAsString(), "$.accessToken");
        return new Session(userId, accessToken);
    }

    private MvcResult login(String identifier, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(identifier, password)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private void createUser(Session admin, String email, Role role) throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Operador Página","email":"%s","cpf":"%s","role":"%s"}
                                """.formatted(email, validCpf(), role.name())))
                .andExpect(status().isCreated());
    }

    private static String bearer(Session session) {
        return "Bearer " + session.accessToken();
    }

    private static String uniqueEmail() {
        return "novo-" + UUID.randomUUID() + "@teste.dev";
    }

    private static String refreshTokenOf(String setCookie) {
        assertThat(setCookie).isNotNull();
        String pair = setCookie.split(";", 2)[0];
        return pair.substring(pair.indexOf('=') + 1);
    }

    private static String validCpf() {
        int[] digits = new int[11];
        for (int i = 0; i < 9; i++) {
            digits[i] = ThreadLocalRandom.current().nextInt(10);
        }
        digits[9] = checkDigit(digits, 9, 10);
        digits[10] = checkDigit(digits, 10, 11);
        StringBuilder builder = new StringBuilder(11);
        for (int digit : digits) {
            builder.append(digit);
        }
        return builder.toString();
    }

    private static int checkDigit(int[] digits, int length, int weight) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += digits[i] * (weight - i);
        }
        int remainder = (sum * 10) % 11;
        return remainder == 10 ? 0 : remainder;
    }

    private record Session(UUID userId, String accessToken) {
    }
}
