package com.maraberto.identity.infra.security;

import com.maraberto.identity.api.IdentityApiExceptionHandler;
import com.maraberto.identity.domain.Role;
import com.maraberto.identity.infra.OperationalUserRepository;
import com.maraberto.shared.security.web.ApiProblemWriter;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
 *
 * <p>O token com {@code mustChangePassword=true} só atravessa a troca de senha,
 * o próprio perfil e o logout. Qualquer outra rota responde 403
 * {@code password-change-required} antes de chegar ao MVC (ADR-027).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final ApiProblemWriter problemWriter;
    private final TokenRevocationService tokenRevocationService;
    private final OperationalUserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, ApiProblemWriter problemWriter) {
        this(jwtService, problemWriter, null, null);
    }

    @Autowired
    public JwtAuthenticationFilter(
            JwtService jwtService,
            ApiProblemWriter problemWriter,
            TokenRevocationService tokenRevocationService,
            OperationalUserRepository userRepository) {
        this.jwtService = jwtService;
        this.problemWriter = problemWriter;
        this.tokenRevocationService = tokenRevocationService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            boolean allowed = authenticate(
                    authHeader.substring(BEARER_PREFIX.length()).trim(), request, response);
            if (!allowed) {
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean authenticate(
            String token,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        if (token.isEmpty() || SecurityContextHolder.getContext().getAuthentication() != null) {
            return true;
        }
        try {
            Claims claims = jwtService.validateAndExtractClaims(token);
            if (JwtService.PURPOSE_MFA_CHALLENGE.equals(claims.get("purpose", String.class))) {
                SecurityContextHolder.clearContext();
                return true;
            }
            String userId = claims.getSubject();
            String roleClaim = claims.get("role", String.class);
            if (userId == null || userId.isBlank() || roleClaim == null || roleClaim.isBlank()) {
                // Token sem as claims mínimas não autentica: seguir anônimo é
                // melhor do que estourar 500 em toda requisição autenticada.
                SecurityContextHolder.clearContext();
                return true;
            }
            UUID userUuid = UUID.fromString(userId);
            Role role;
            try {
                role = Role.valueOf(roleClaim);
            } catch (IllegalArgumentException unknownRole) {
                SecurityContextHolder.clearContext();
                return true;
            }
            if (tokenRevocationService != null) {
                Date issuedAtDate = claims.getIssuedAt();
                Instant issuedAt = issuedAtDate == null ? null : issuedAtDate.toInstant();
                if (tokenRevocationService.isRevoked(claims.getId(), userUuid, issuedAt)) {
                    SecurityContextHolder.clearContext();
                    return true;
                }
            }
            if (userRepository != null) {
                Optional<Role> activeRole = userRepository.findActiveRoleById(userUuid);
                if (activeRole.isEmpty() || activeRole.get() != role) {
                    SecurityContextHolder.clearContext();
                    return true;
                }
            }
            if (Boolean.TRUE.equals(claims.get("mustChangePassword", Boolean.class))
                    && !passwordChangeAllowed(request)) {
                problemWriter.write(
                        request,
                        response,
                        HttpStatus.FORBIDDEN,
                        IdentityApiExceptionHandler.PASSWORD_CHANGE_REQUIRED,
                        "Troca de senha obrigatória",
                        "Defina uma nova senha para continuar.");
                return false;
            }
            var authentication = new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    List.of(new SimpleGrantedAuthority(role.authority())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            return true;
        }
    }

    private static boolean passwordChangeAllowed(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("OPTIONS".equals(method)) {
            return true;
        }
        if ("GET".equals(method) && "/api/v1/auth/me".equals(path)) {
            return true;
        }
        return "POST".equals(method)
                && ("/api/v1/auth/password".equals(path)
                    || "/api/v1/auth/logout".equals(path)
                    || "/api/v1/auth/refresh".equals(path));
    }
}
