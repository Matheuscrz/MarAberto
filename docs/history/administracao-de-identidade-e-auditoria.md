# Histórico de Entregas — Administração de identidade e auditoria

> Registro da entrega da administração de identidade e da trilha de auditoria,
> concluída em 2026-11-24. Para o estado operacional vigente, consulte
> `AGENTS.md`, `docs/Architecture.md`, `docs/Requirements.md` e a ADR-027.

## Escopo entregue

- **Administração real de usuários pela TI (RF20).** O módulo `identity` passou a
  expor `/api/v1/admin/users` (somente `ADMIN`), substituindo o
  `features/admin/api/demo-users.ts` do frontend por dados do servidor:

  | Método | Rota | Efeito |
  | :-- | :-- | :-- |
  | `GET` | `/api/v1/admin/users?active=&role=&q=&cursor=&limit=` | página por cursor (`seq`), busca por e-mail/CPF exatos ou prefixo da matrícula |
  | `POST` | `/api/v1/admin/users` | cria operador com senha temporária e `must_change_password=true` |
  | `PATCH` | `/api/v1/admin/users/{id}` | altera `role` e/ou `active`; revoga as sessões |
  | `POST` | `/api/v1/admin/users/{id}/password-reset` | nova senha temporária, revoga as sessões |

  **Não existe `DELETE`.** Desativar preserva plantões, ocorrências e a própria
  trilha; reativar é `PATCH {active: true}`. O `ADMIN` não consegue tirar o
  próprio papel de `ADMIN` nem se desativar (`SelfAdminProtectionException`,
  `409`). Nome, e-mail e CPF continuam cifrados; a resposta mascara o e-mail e
  nunca devolve CPF.
- **Senha temporária com troca obrigatória.** O servidor gera 16 caracteres com
  `SecureRandom` (alfabeto sem `I`, `l`, `O`, `0`, `1`) e devolve o valor **uma
  única vez** na criação/reset. Só o hash Argon2 é gravado, com validade de 24 h
  (`maraberto.identity.temporary-password-ttl`). Enquanto
  `must_change_password=true`, o `JwtAuthenticationFilter` responde
  `403 password-change-required` (RFC 9457) em toda rota de negócio e libera
  apenas `GET /auth/me`, `POST /auth/password`, `POST /auth/refresh` e
  `POST /auth/logout`. A senha definitiva (`POST /api/v1/auth/password`) exige
  no mínimo 12 caracteres (piso de 8), letra e número, recusa a senha atual e as
  senhas óbvias, limpa a marca e revoga as sessões.
- **Trilha `audit_log` append-only (RNF06).** Migração `V25` cria a tabela com
  `BIGSERIAL` (cursor `occurred_at` + `id`), `trace_id`, `ip_bidx` (índice cego,
  nunca o IP) e `detail jsonb` sem PII. `prev_hash`/`hash` são `CHAR(64)` com
  `UNIQUE`, formando uma cadeia linear; a escrita toma
  `pg_advisory_xact_lock` antes de ler o último hash e participa da transação do
  ato auditado (falha ao auditar derruba o ato). Um trigger recusa `UPDATE` e
  `DELETE` até do dono da tabela.
- **Hash canônico pelo PostgreSQL.** O `detail` é normalizado com
  `CAST(CAST(:detail AS jsonb) AS text)` antes do SHA-256: `jsonb` reordena
  chaves e usa espaçamento diferente do Jackson, e calcular o hash sobre o JSON
  do cliente fazia toda conferência acusar divergência. Escrita e verificação
  usam o mesmo texto.
- **Consulta e integridade.** `GET /api/v1/admin/audit` filtra por `actorId`,
  `action`, `entityType`, `entityId`, `traceId`, `from`/`to` e pagina por cursor
  opaco (`limit` 1–100, padrão 50), sempre `DESC` e `Cache-Control: no-store`.
  `GET /api/v1/admin/audit/integrity` recalcula a cadeia inteira e devolve
  `checked`, `valid` e `firstInvalidId`.
- **`TraceIdFilter`.** Gera/propaga o `traceId` no MDC e no cabeçalho
  `X-Request-Id`; é esse valor que liga a linha da trilha ao log estruturado da
  requisição.
- **Instrumentação dos atos sensíveis.** `AuditActions`/`AuditEntityTypes`
  centralizam os nomes e a porta `audit/api/AuditTrail` é chamada na mesma
  transação por: `AdminUserService` (`USER_CREATED`, `USER_ACTIVATED`,
  `USER_DEACTIVATED`, `USER_ROLE_CHANGED`, `USER_PASSWORD_RESET`),
  `AuthenticationService` (`PASSWORD_CHANGED`), `StationService`
  (`STATION_ACTIVATED`/`STATION_DEACTIVATED`), `UnmappedSignalService`
  (`UNMAPPED_SIGNAL_DECIDED`), `BathingAreaImportService` (`IMPORT_APPLIED`),
  `ShiftService.closeIdleShifts` (`SHIFT_CLOSED_IDLE`) e
  `OccurrenceCommandService` (`OCCURRENCE_CLOSED`, `OCCURRENCE_EDITED`,
  `PERSON_REVEALED` — sem o valor revelado).
- **Frontend.** Nova tela `/trocar-senha` (fora do shell, acessível também com
  senha temporária), guarda `RequireRole` redirecionando quem tem
  `mustChangePassword` e login levando a ela; `/admin/usuarios` reescrita sobre a
  API real, com cadastro, filtros, paginação, ativar/desativar, troca de papel,
  reset e painel da senha temporária exibida uma única vez; `/admin/auditoria`
  com filtros, cursor, detalhe e conferência de integridade; item "Auditoria" na
  navegação e `AdminHomePage` sem dados de demonstração.

## Migrações

- `V24__identity_user_administration.sql`: `must_change_password`,
  `temporary_password_expires_at`, `password_changed_at`, `seq bigserial`
  (cursor da lista) e `idx_auth_sessions_user_last_used`.
- `V25__audit_log.sql`: tabela `audit_log`, índices, trigger append-only e
  comentários.

## Testes e verificação

- `AdminUserIntegrationTests` cobre criar → trocar senha → login, bloqueio de
  rota com `must_change_password`, reset invalidando a senha antiga, desativação
  derrubando refresh, autoproteção do `ADMIN` e leitura da trilha.
- `JwtAuthenticationFilterTests` e `StompAuthenticationInterceptorTests`
  atualizados para a claim `mustChangePassword`.
- `ModularityTests` verde: `audit` não importa módulos de domínio.
- Suíte backend completa (`./gradlew test`) verde, `npm run lint`,
  `npm run build` e `docker compose build` verdes.

## Pendências conhecidas

- `LOGIN_FAILED` não entra nesta fatia: o agregado por IP/janela nasce junto com
  a blacklist de JWT e o MFA (TOTP), na migration `V26`.
- O job de expurgo por retenção (`maraberto.audit.retention`, 5 anos) ainda não
  foi implementado; a trilha só cresce.
- `audit_log` não pode ser apagada nem em teste (trigger), então a tabela
  acumula linhas entre execuções da suíte de integração.
