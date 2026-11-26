package com.maraberto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.maraberto.identity.api.IdentityApiExceptionHandler;
import com.maraberto.identity.domain.Role;
import com.maraberto.identity.infra.AdminBootstrapRunner;
import com.maraberto.identity.infra.AuthSessionRepository;
import com.maraberto.identity.infra.OperationalUserRepository;
import com.maraberto.identity.infra.UserRecoveryCodeRepository;
import com.maraberto.identity.infra.security.TotpGenerator;
import com.maraberto.shared.security.crypto.BlindIndexer;
import com.maraberto.support.PostgisIntegrationTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AdminBootstrapAndMfaIntegrationTests extends PostgisIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OperationalUserRepository users;

    @Autowired
    private AuthSessionRepository sessions;

    @Autowired
    private UserRecoveryCodeRepository recoveryCodes;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private BlindIndexer blindIndexer;

    @Autowired
    private TotpGenerator totpGenerator;

    @BeforeEach
    void clean() {
        recoveryCodes.deleteAll();
        sessions.deleteAll();
        users.deleteAll();
    }

    @Test
    void bootstrapAdminRequiresPasswordChangeThenSupportsMfaAndJwtLogoutRevocation() throws Exception {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                users, passwordEncoder, blindIndexer, "admin", "admin@maraberto.local", "admin", "12345678909");
        runner.run(new DefaultApplicationArguments());
        // Idempotente: rodar novamente não duplica nem falha.
        runner.run(new DefaultApplicationArguments());
        assertThat(users.existsByRoleAndActiveTrue(Role.ADMIN)).isTrue();

        // 1. Login inicial com admin / admin devolve mustChangePassword=true.
        MvcResult firstLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"identifier":"admin","password":"admin"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andExpect(jsonPath("$.mfaRequired").value(false))
                .andReturn();

        String initialToken = JsonPath.read(firstLogin.getResponse().getContentAsString(), "$.accessToken");

        // 2. Rotas administrativas bloqueadas até trocar a senha.
        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + initialToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(IdentityApiExceptionHandler.PASSWORD_CHANGE_REQUIRED));

        // 3. Troca de senha definitiva revoga o token antigo imediatamente.
        mockMvc.perform(post("/api/v1/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + initialToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"admin","newPassword":"NovaSenhaForte123"}
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + initialToken))
                .andExpect(status().isUnauthorized());

        // 4. Novo login com a senha definitiva.
        MvcResult definitiveLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"identifier":"admin","password":"NovaSenhaForte123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(false))
                .andReturn();
        String accessToken = JsonPath.read(definitiveLogin.getResponse().getContentAsString(), "$.accessToken");

        // 5. Ativar MFA TOTP e receber 8 códigos de recuperação.
        MvcResult setupRes = mockMvc.perform(post("/api/v1/auth/mfa/setup")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").isNotEmpty())
                .andExpect(jsonPath("$.otpauthUri").isNotEmpty())
                .andReturn();
        String secret = JsonPath.read(setupRes.getResponse().getContentAsString(), "$.secret");
        String validCode = totpGenerator.currentCode(secret, Instant.now());

        MvcResult confirmRes = mockMvc.perform(post("/api/v1/auth/mfa/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"code\":\"" + validCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes.length()").value(8))
                .andReturn();
        List<String> codes = JsonPath.read(confirmRes.getResponse().getContentAsString(), "$.recoveryCodes");

        // 6. Logout revoga imediatamente o access token (blacklist jti).
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());

        // 7. Próximo login exige desafio MFA e aceita código de recuperação (uso único).
        MvcResult mfaLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"identifier":"admin","password":"NovaSenhaForte123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.mfaChallengeToken").isNotEmpty())
                .andReturn();
        String challengeToken = JsonPath.read(mfaLogin.getResponse().getContentAsString(), "$.mfaChallengeToken");

        // O challengeToken não dá acesso direto a /me.
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + challengeToken))
                .andExpect(status().isUnauthorized());

        // Concluir login usando o primeiro código de recuperação.
        MvcResult verified = mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .contentType(APPLICATION_JSON)
                        .content("{\"challengeToken\":\"" + challengeToken + "\",\"code\":\"" + codes.get(0) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.mfaRequired").value(false))
                .andReturn();
        assertThat((String) JsonPath.read(verified.getResponse().getContentAsString(), "$.accessToken"))
                .isNotBlank();

        // Reuso do mesmo código de recuperação falha.
        mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .contentType(APPLICATION_JSON)
                        .content("{\"challengeToken\":\"" + challengeToken + "\",\"code\":\"" + codes.get(0) + "\"}"))
                .andExpect(status().isUnauthorized());
    }
}
