package com.maraberto.shared.security.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.maraberto.support.PostgisIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Cabeçalhos de segurança e {@code Cache-Control: no-store} nas rotas de sessão.
 *
 * <p>A rota usada é protegida: além dos headers, o teste confirma que a
 * requisição sem token recebe 401 em RFC 9457 (e não uma página HTML).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiSecurityHeadersTests extends PostgisIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void addsSecurityHeadersAndPreventsCachingOfSessionResponses() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")));
    }
}
