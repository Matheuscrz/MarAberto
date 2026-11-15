package com.maraberto.identity.infra.security;

import com.maraberto.identity.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica a requisição a partir do access token JWT.
 *
 * <p>Token ausente segue como anônimo; token inválido/expirado limpa o contexto
 * e deixa a decisão para o {@code AuthenticationEntryPoint} (401 em RFC 9457).
 * Assim endpoints públicos continuam funcionando mesmo com um token velho no
 * cliente.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            authenticate(authHeader.substring(BEARER_PREFIX.length()).trim());
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        if (token.isEmpty() || SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }
        try {
            Claims claims = jwtService.validateAndExtractClaims(token);
            String userId = claims.getSubject();
            String roleClaim = claims.get("role", String.class);
            if (userId == null || userId.isBlank() || roleClaim == null || roleClaim.isBlank()) {
                // Token sem as claims mínimas não autentica: seguir anônimo é
                // melhor do que estourar 500 em toda requisição autenticada.
                SecurityContextHolder.clearContext();
                return;
            }
            Role role;
            try {
                role = Role.valueOf(roleClaim);
            } catch (IllegalArgumentException unknownRole) {
                SecurityContextHolder.clearContext();
                return;
            }
            var authentication = new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    List.of(new SimpleGrantedAuthority(role.authority())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
        }
    }
}
