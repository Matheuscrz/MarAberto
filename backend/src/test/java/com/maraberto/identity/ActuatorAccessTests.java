package com.maraberto.identity;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.maraberto.identity.api.dto.RegisterOperationalUserCommand;
import com.maraberto.identity.application.AuthenticationService;
import com.maraberto.identity.domain.Role;
import com.maraberto.identity.infra.AuthSessionRepository;
import com.maraberto.identity.infra.OperationalUserRepository;
import com.maraberto.support.PostgisIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Acesso às métricas do actuator.
 *
 * <p>O {@code /actuator/metrics} publica JVM, pools e contadores HTTP. Antes
 * desta regra ele caía no {@code anyRequest().authenticated()} e qualquer
 * sessão — inclusive o guarda-vidas de campo — lia os números da instância.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ActuatorAccessTests extends PostgisIntegrationTest {

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
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lifeguardIsForbidden() throws Exception {
        createUser("operador@maraberto.dev", "BM-10492", "11144477735", Role.LIFEGUARD);

        mockMvc.perform(get("/actuator/metrics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenOf("operador@maraberto.dev")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminReadsTheMetrics() throws Exception {
        createUser("ti@maraberto.dev", "TI-0001", "11144477735", Role.ADMIN);

        mockMvc.perform(get("/actuator/metrics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenOf("ti@maraberto.dev")))
                .andExpect(status().isOk());
    }

    @Test
    void healthStaysPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    private void createUser(String email, String badgeNumber, String cpf, Role role) {
        authenticationService.registerOperational(new RegisterOperationalUserCommand(
                "Operador Teste", badgeNumber, email, cpf, PASSWORD, role));
    }

    private String accessTokenOf(String identifier) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(identifier, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }
}
