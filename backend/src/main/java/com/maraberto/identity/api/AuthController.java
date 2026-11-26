package com.maraberto.identity.api;

import com.maraberto.identity.api.dto.AuthResponse;
import com.maraberto.identity.api.dto.AuthenticatedUserResponse;
import com.maraberto.identity.api.dto.ChangePasswordRequest;
import com.maraberto.identity.api.dto.LoginCommand;
import com.maraberto.identity.api.dto.MfaCodeRequest;
import com.maraberto.identity.api.dto.MfaConfirmResponse;
import com.maraberto.identity.api.dto.MfaDisableRequest;
import com.maraberto.identity.api.dto.MfaSetupResponse;
import com.maraberto.identity.api.dto.MfaVerifyRequest;
import com.maraberto.identity.application.AuthenticationService;
import com.maraberto.identity.application.MfaService;
import com.maraberto.identity.application.OriginVerifier;
import com.maraberto.identity.application.RequestMetadata;
import com.maraberto.identity.infra.security.CookieProperties;
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
    private final MfaService mfaService;
    private final JwtProperties jwtProperties;
    private final CookieProperties cookieProperties;
    private final OriginVerifier originVerifier;

    public AuthController(
            AuthenticationService authenticationService,
            MfaService mfaService,
            JwtProperties jwtProperties,
            CookieProperties cookieProperties,
            OriginVerifier originVerifier) {
        this.authenticationService = authenticationService;
        this.mfaService = mfaService;
        this.jwtProperties = jwtProperties;
        this.cookieProperties = cookieProperties;
        this.originVerifier = originVerifier;
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
        if (result.refreshToken() == null) {
            return ResponseEntity.ok(result.response());
        }
        return withRefreshCookie(result, jwtProperties.getRefreshTokenTtl());
    }

    @PostMapping("/mfa/verify")
    @Operation(summary = "Validar segundo fator (TOTP ou código de recuperação)",
            description = "Conclui o login iniciado quando mfaRequired=true.")
    public ResponseEntity<AuthResponse> verifyMfa(
            @Valid @RequestBody MfaVerifyRequest command,
            HttpServletRequest request) {
        AuthenticationService.LoginResult result = authenticationService.verifyMfa(
                command.challengeToken(), command.code(), metadata(request));
        return withRefreshCookie(result, jwtProperties.getRefreshTokenTtl());
    }

    @PostMapping("/mfa/setup")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Iniciar configuração de MFA (TOTP)",
            description = "Gera segredo Base32 e URI otpauth:// para Coordenação e TI.")
    public MfaSetupResponse setupMfa(Authentication authentication) {
        return mfaService.setup(UUID.fromString(authentication.getName()));
    }

    @PostMapping("/mfa/confirm")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Confirmar ativação de MFA (TOTP)",
            description = "Valida o primeiro código TOTP e retorna 8 códigos de recuperação de uso único.")
    public MfaConfirmResponse confirmMfa(
            @Valid @RequestBody MfaCodeRequest command,
            Authentication authentication,
            HttpServletRequest request) {
        return mfaService.confirm(
                UUID.fromString(authentication.getName()), command.code(), metadata(request));
    }

    @PostMapping("/mfa/disable")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Desativar MFA (TOTP)",
            description = "Exige senha atual e código TOTP ou código de recuperação; revoga todas as sessões.")
    public ResponseEntity<Void> disableMfa(
            @Valid @RequestBody MfaDisableRequest command,
            Authentication authentication,
            HttpServletRequest request) {
        mfaService.disable(
                UUID.fromString(authentication.getName()),
                command.currentPassword(),
                command.code(),
                metadata(request));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE,
                        AuthCookie.clear(cookieProperties.isSecure()).toString())
                .build();
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
            HttpServletRequest request) {
        originVerifier.verify(request);
        AuthenticationService.LoginResult result = authenticationService.refresh(
                AuthCookie.read(request, cookieProperties.isSecure()), metadata(request));
        return withRefreshCookie(result, jwtProperties.getRefreshTokenTtl());
    }

    @PostMapping("/logout")
    @Operation(summary = "Encerrar a sessão",
            description = "Revoga a família de refresh e apaga o cookie. Idempotente.")
    @ApiResponse(responseCode = "204", description = "Sessão encerrada")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        originVerifier.verify(request);
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String bearer = (authHeader != null && authHeader.startsWith("Bearer "))
                ? authHeader.substring(7).trim()
                : null;
        authenticationService.logout(AuthCookie.read(request, cookieProperties.isSecure()), bearer);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, AuthCookie.clear(cookieProperties.isSecure()).toString())
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

    @PostMapping("/password")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Trocar a senha",
            description = "Valida a senha atual, aplica a política e revoga todas as sessões. "
                    + "Define a senha definitiva quando a temporária está pendente.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Senha alterada; entre novamente"),
            @ApiResponse(responseCode = "400", description = "Senha nova não atende à política (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "401", description = "Senha atual incorreta (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "403", description = "Senha temporária expirada (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest command,
            Authentication authentication,
            HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String bearer = (authHeader != null && authHeader.startsWith("Bearer "))
                ? authHeader.substring(7).trim()
                : null;
        authenticationService.changePassword(
                UUID.fromString(authentication.getName()),
                command.currentPassword(),
                command.newPassword(),
                metadata(request),
                bearer);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE,
                        AuthCookie.clear(cookieProperties.isSecure()).toString())
                .build();
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(
            AuthenticationService.LoginResult result,
            Duration maxAge) {
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE,
                        AuthCookie.create(result.refreshToken(), maxAge, cookieProperties.isSecure())
                                .toString())
                .body(result.response());
    }

    private RequestMetadata metadata(HttpServletRequest request) {
        // O container resolve X-Forwarded-For apenas de proxy confiável
        // (`server.forward-headers-strategy=native`); ler o cabeçalho à mão
        // permitiria que qualquer cliente forjasse o próprio IP.
        return new RequestMetadata(request.getHeader(HttpHeaders.USER_AGENT), request.getRemoteAddr());
    }
}
