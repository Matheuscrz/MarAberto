package com.maraberto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.maraberto.identity.domain.Role;
import com.maraberto.identity.infra.security.JwtAuthenticationFilter;
import com.maraberto.identity.infra.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);

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

    private MockHttpServletResponse doFilter(String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.addHeader("Authorization", authorization);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
