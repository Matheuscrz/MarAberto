package com.maraberto.identity.api;

import com.maraberto.identity.api.dto.AuthResponse;
import com.maraberto.identity.api.dto.AuthenticatedUserResponse;
import com.maraberto.identity.api.dto.LoginCommand;
import com.maraberto.identity.application.AuthenticationService;
import com.maraberto.identity.application.RequestMetadata;
import com.maraberto.identity.infra.security.JwtProperties;
import com.maraberto.shared.api.ApiProblem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login único e ciclo de vida da sessão dos operadores.
 *
 * <p>O access token volta no corpo (o cliente guarda só em memória); o refresh
 * opaco vai no cookie {@code __Secure-maraberto_rt}. As respostas de erro seguem
 * RFC 9457.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticação", description = "Login único, refresh, logout e operador autenticado")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final JwtProperties jwtProperties;

    public AuthController(AuthenticationService authenticationService, JwtProperties jwtProperties) {
        this.authenticationService = authenticationService;
        this.jwtProperties = jwtProperties;
    }

    @PostMapping("/login")
    @Operation(summary = "Autenticar operador",
            description = "Aceita e-mail, CPF ou matrícula. Abre a sessão de refresh e devolve o access token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sessão emitida",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "401", description = "Credenciais inválidas (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "403", description = "Conta inativa (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginCommand command,
            HttpServletRequest request) {
        AuthenticationService.LoginResult result =
                authenticationService.login(command, metadata(request));
        return withRefreshCookie(result, jwtProperties.getRefreshTokenTtl());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar a sessão",
            description = "Troca o refresh opaco do cookie por um novo par access + refresh, com rotação.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sessão renovada",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão inválida ou expirada (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = AuthCookie.NAME, required = false) String refreshToken,
            HttpServletRequest request) {
        AuthenticationService.LoginResult result =
                authenticationService.refresh(refreshToken, metadata(request));
        return withRefreshCookie(result, jwtProperties.getRefreshTokenTtl());
    }

    @PostMapping("/logout")
    @Operation(summary = "Encerrar a sessão",
            description = "Revoga a família de refresh e apaga o cookie. Idempotente.")
    @ApiResponse(responseCode = "204", description = "Sessão encerrada")
    public ResponseEntity<Void> logout(
            @CookieValue(name = AuthCookie.NAME, required = false) String refreshToken) {
        authenticationService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, AuthCookie.clear().toString())
                .build();
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Operador autenticado",
            description = "Dados públicos do operador dono do access token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Operador autenticado",
                    content = @Content(schema = @Schema(implementation = AuthenticatedUserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Não autenticado (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public AuthenticatedUserResponse me(Authentication authentication) {
        return authenticationService.currentUser(UUID.fromString(authentication.getName()));
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(
            AuthenticationService.LoginResult result,
            Duration maxAge) {
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE,
                        AuthCookie.create(result.refreshToken(), maxAge).toString())
                .body(result.response());
    }

    private RequestMetadata metadata(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String clientIp = forwardedFor == null || forwardedFor.isBlank()
                ? request.getRemoteAddr()
                : forwardedFor.split(",")[0].trim();
        return new RequestMetadata(request.getHeader(HttpHeaders.USER_AGENT), clientIp);
    }
}
