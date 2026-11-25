package com.maraberto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.maraberto.identity.domain.Role;
import com.maraberto.identity.infra.security.JwtAuthenticationFilter;
import com.maraberto.identity.infra.security.JwtService;
import com.maraberto.shared.security.web.ApiProblemWriter;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Token bem assinado mas sem a claim {@code role} é um caso real de versão
 * antiga/erro de emissão. A requisição deve seguir anônima (e o endpoint
 * autenticado responde 401), nunca estourar 500 dentro do filtro.
 */
class JwtAuthenticationFilterTests {

    private static final UUID USER_ID = UUID.randomUUID();

    private final JwtService jwtService = mock(JwtService.class);
    private final ApiProblemWriter problemWriter = mock(ApiProblemWriter.class);
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(jwtService, problemWriter);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingRoleClaimLeavesRequestAnonymousWithoutFailing() throws Exception {
        Claims claims = Jwts.claims().subject(USER_ID.toString()).build();
        when(jwtService.validateAndExtractClaims("sem-role")).thenReturn(claims);

        MockHttpServletResponse response = doFilter("Bearer sem-role");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void unknownRoleStillDoesNotFail() throws Exception {
        Claims claims = Jwts.claims().subject(USER_ID.toString()).add("role", "SUPER_HACKER").build();
        when(jwtService.validateAndExtractClaims("role-invalido")).thenReturn(claims);

        MockHttpServletResponse response = doFilter("Bearer role-invalido");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void validTokenAuthenticatesWithRoleAuthority() throws Exception {
        Claims claims = Jwts.claims().subject(USER_ID.toString()).add("role", "COORDINATOR").build();
        when(jwtService.validateAndExtractClaims("valido")).thenReturn(claims);

        doFilter("Bearer valido");

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo(USER_ID.toString());
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_" + Role.COORDINATOR.name());
    }

    @Test
    void temporaryPasswordBlocksBusinessRoutes() throws Exception {
        Claims claims = Jwts.claims().subject(USER_ID.toString())
                .add("role", "LIFEGUARD")
                .add("mustChangePassword", true)
                .build();
        when(jwtService.validateAndExtractClaims("temporaria")).thenReturn(claims);

        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/occurrences");
        request.addHeader("Authorization", "Bearer temporaria");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(problemWriter).write(
                eq(request),
                eq(response),
                eq(HttpStatus.FORBIDDEN),
                anyString(),
                anyString(),
                anyString());
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void temporaryPasswordMayChangeThePassword() throws Exception {
        Claims claims = Jwts.claims().subject(USER_ID.toString())
                .add("role", "LIFEGUARD")
                .add("mustChangePassword", true)
                .build();
        when(jwtService.validateAndExtractClaims("temporaria")).thenReturn(claims);

        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/auth/password");
        request.addHeader("Authorization", "Bearer temporaria");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    private MockHttpServletResponse doFilter(String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.addHeader("Authorization", authorization);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
