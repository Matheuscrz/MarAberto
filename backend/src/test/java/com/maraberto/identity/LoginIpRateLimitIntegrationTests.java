package com.maraberto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.maraberto.identity.api.IdentityApiExceptionHandler;
import com.maraberto.support.PostgisIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Limite por origem do login. Contexto próprio, com capacidade baixa, para não
 * depender do balde acumulado pelas outras classes.
 */
@SpringBootTest(properties = {
        "maraberto.security.login.ip-capacity=2",
        "maraberto.security.login.ip-window=PT5M"
})
@AutoConfigureMockMvc
class LoginIpRateLimitIntegrationTests extends PostgisIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void thirdAttemptFromTheSameOriginIsRejectedWithRetryAfter() throws Exception {
        for (int attempt = 0; attempt < 2; attempt += 1) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {"identifier":"ninguem@maraberto.dev","password":"senhaerrada"}
                                    """))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"identifier":"ninguem@maraberto.dev","password":"senhaerrada"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.type").value(IdentityApiExceptionHandler.TOO_MANY_ATTEMPTS));
    }
}
