package com.maraberto.identity.api;

import com.maraberto.identity.api.dto.AdminUserPage;
import com.maraberto.identity.api.dto.AdminUserView;
import com.maraberto.identity.api.dto.CreateAdminUserRequest;
import com.maraberto.identity.api.dto.ProvisionedAdminUserResponse;
import com.maraberto.identity.api.dto.UpdateAdminUserRequest;
import com.maraberto.identity.api.dto.UpdateUserBeachScopesRequest;
import com.maraberto.identity.api.dto.UserBeachScopesView;
import com.maraberto.identity.application.AdminUserService;
import com.maraberto.identity.application.RequestMetadata;
import com.maraberto.identity.application.UserBeachScopeService;
import com.maraberto.identity.domain.Role;
import com.maraberto.shared.api.ApiProblem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administração do efetivo pela TI (`ADMIN`, ADR-027).
 *
 * <p>Criação e reset devolvem a senha temporária **uma única vez**; a resposta é
 * {@code no-store}. Não há {@code DELETE}: desativar preserva o histórico.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Usuários (TI)", description = "Cadastro, papel, ativação e reset de senha")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final UserBeachScopeService userBeachScopeService;

    public AdminUserController(
            AdminUserService adminUserService,
            UserBeachScopeService userBeachScopeService) {
        this.adminUserService = adminUserService;
        this.userBeachScopeService = userBeachScopeService;
    }

    @GetMapping
    @Operation(summary = "Listar usuários",
            description = "Página do cadastro mais recente para o mais antigo. Filtre por "
                    + "`active`, `role` e `q` (e-mail/CPF exatos ou prefixo da matrícula); "
                    + "devolva `nextCursor` em `cursor` para a próxima página.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de usuários",
                    content = @Content(schema = @Schema(implementation = AdminUserPage.class))),
            @ApiResponse(responseCode = "400", description = "Filtro ou cursor inválido (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "403", description = "Sem papel de TI (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public ResponseEntity<AdminUserPage> list(
            @Parameter(description = "Filtra por estado; omitido devolve ativos e inativos")
            @RequestParam(required = false) Boolean active,
            @Parameter(description = "Filtra por papel")
            @RequestParam(required = false) Role role,
            @Parameter(description = "E-mail/CPF exato ou prefixo da matrícula")
            @RequestParam(required = false) String q,
            @Parameter(description = "Tamanho da página (1 a 100)", example = "30")
            @RequestParam(required = false, defaultValue = "30") int limit,
            @Parameter(description = "Cursor opaco devolvido em nextCursor")
            @RequestParam(required = false) String cursor) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(adminUserService.list(active, role, q, limit, cursor));
    }

    @PostMapping
    @Operation(summary = "Cadastrar operador",
            description = "Gera a senha temporária, devolve uma única vez e exige a troca "
                    + "no primeiro acesso. A conta já nasce ativa.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Operador criado",
                    content = @Content(schema = @Schema(implementation = ProvisionedAdminUserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Payload inválido (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "409", description = "CPF, e-mail ou matrícula já cadastrados (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public ResponseEntity<ProvisionedAdminUserResponse> create(
            @Valid @RequestBody CreateAdminUserRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        ProvisionedAdminUserResponse response = adminUserService.create(
                actor(authentication), request, metadata(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(response);
    }

    @PatchMapping("/{userId}")
    @Operation(summary = "Alterar papel ou ativação",
            description = "Omitir um campo mantém o valor atual. Desativar revoga as sessões "
                    + "e impede login; trocar o papel também revoga as sessões.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Operador atualizado",
                    content = @Content(schema = @Schema(implementation = AdminUserView.class))),
            @ApiResponse(responseCode = "400", description = "Payload inválido (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "404", description = "Operador inexistente (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class))),
            @ApiResponse(responseCode = "409", description = "Tentativa de retirar o próprio acesso de TI (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public AdminUserView update(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateAdminUserRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        return adminUserService.update(
                actor(authentication), userId, request, metadata(httpRequest));
    }

    @PostMapping("/{userId}/password-reset")
    @Operation(summary = "Redefinir a senha",
            description = "Gera outra senha temporária, revoga todas as sessões e exige a "
                    + "troca no próximo login. Funciona com a conta bloqueada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nova senha temporária",
                    content = @Content(schema = @Schema(implementation = ProvisionedAdminUserResponse.class))),
            @ApiResponse(responseCode = "404", description = "Operador inexistente (RFC 9457)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    })
    public ResponseEntity<ProvisionedAdminUserResponse> resetPassword(
            @PathVariable UUID userId,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(adminUserService.resetPassword(
                        actor(authentication), userId, metadata(httpRequest)));
    }

    @GetMapping("/{userId}/beach-scopes")
    @Operation(summary = "Consultar escopo territorial do usuário",
            description = "Devolve as áreas de banho vinculadas ao usuário (lista vazia = escopo irrestrito).")
    public UserBeachScopesView getBeachScopes(@PathVariable UUID userId) {
        return userBeachScopeService.getScopes(userId);
    }

    @PutMapping("/{userId}/beach-scopes")
    @Operation(summary = "Definir escopo territorial do usuário",
            description = "Substitui a lista de áreas de banho visíveis por um coordenador regional. "
                    + "Enviar lista vazia remove a restrição.")
    public UserBeachScopesView replaceBeachScopes(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserBeachScopesRequest request,
            Authentication authentication) {
        return userBeachScopeService.replaceScopes(actor(authentication), userId, request.beachIds());
    }

    private static UUID actor(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }

    private static RequestMetadata metadata(HttpServletRequest request) {
        return new RequestMetadata(
                request.getHeader(HttpHeaders.USER_AGENT), request.getRemoteAddr());
    }
}
