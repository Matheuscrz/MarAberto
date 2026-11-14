# ADR-027: Administração de identidade e trilha de auditoria

- Status: **Proposto (2026-11-13)** — decisão a confirmar ao abrir a branch
  `32-feature-administracao-de-identidade-e-auditoria`.
- Relacionadas: ADR-009 (autenticação embutida), ADR-012 (dados sensíveis), ADR-024
  (ciclo de vida da estrutura), ADR-026 (painel da coordenação).
- Requisitos: RF20, RF36, RNF06, RNF18; `operational_users`, `auth_sessions`, módulo
  `audit`.

## Contexto

O módulo `identity` implementa login, refresh, logout e RBAC (ADR-009), mas **não expõe
administração de usuários**: não há como a TI criar uma conta, desativar quem saiu, corrigir
um papel ou redefinir uma senha. A tela `/admin/usuarios` é preenchida por
`features/admin/api/demo-users.ts` — dados locais que nunca chegam ao servidor.

O módulo `audit` existe apenas como `package-info` ("trilha imutável, chamada de forma
síncrona na mesma transação do ato auditado"). Não há tabela, porta pública nem consulta.
Hoje cada módulo guarda o que precisa em eventos próprios (`occurrence_events`,
`import_batches`), o que não responde à pergunta da TI: **"quem fez o quê, quando, de onde?"**

O perfil mínimo do projeto não tem SMTP (ADR-009): convite por e-mail não é uma opção
disponível.

## Decisão — parte 1: administração de usuários

### 1.1 Endpoints (`ADMIN`, `/api/v1/admin/users`)

| Método | Rota | Efeito |
| :-- | :-- | :-- |
| `GET` | `/api/v1/admin/users?active=&role=&cursor=&limit=` | lista paginada (cursor), e-mail mascarado |
| `POST` | `/api/v1/admin/users` | cria operador com **senha temporária** e `must_change_password=true` |
| `PATCH` | `/api/v1/admin/users/{id}` | altera `role` e/ou `active` (desativar revoga sessões) |
| `POST` | `/api/v1/admin/users/{id}/password-reset` | nova senha temporária, revoga sessões, `must_change_password=true` |

- **Nada de `DELETE`:** plantões, ocorrências e auditoria apontam para o usuário; desativar
  preserva o histórico (RNF06). Reativar é `PATCH {active: true}`.
- **Papel** continua o enum de três valores do ADR-009 (`LIFEGUARD`, `COORDINATOR`,
  `ADMIN`). A coordenação pode ativar/desativar guarda-vidas (RF20), mas não criar contas
  nem mexer em `ADMIN` — a rota de criação/reset é exclusiva da TI.
- **Desativar** marca `active=false`, **revoga todas as sessões** (`revokeAllForUser`, já
  existente) e impede login. O access token em uso é JWT de 15 min e morre sozinho; não se
  cria blacklist nesta fatia.

### 1.2 Senha temporária e troca obrigatória

- **Geração:** 16 caracteres aleatórios (`SecureRandom`) em alfabeto sem caracteres
  ambíguos, devolvidos **uma única vez** na resposta da criação/reset. Não há e-mail; o
  administrador entrega presencialmente ou por canal institucional.
- **Armazenamento:** apenas o hash Argon2 (`Argon2PasswordEncoder` já configurado) e
  `must_change_password=true` com `temporary_password_expires_at` (24 h).
- **Login com senha temporária:** a resposta devolve sessão, mas o token carrega a marca
  `mustChangePassword=true` e o filtro de segurança só libera
  `POST /api/v1/auth/password` (e `GET /api/v1/auth/me`). Qualquer outra rota responde
  `403 password-change-required` em RFC 9457.
- **Definir a senha definitiva:** `POST /api/v1/auth/password {currentPassword, newPassword}`
  valida a temporária, aplica a política (mínimo configurável, recusa de senha igual à
  anterior e de lista de senhas óbvias), limpa `must_change_password` e expira a temporária
  na mesma transação.
- **Reset pela TI** funciona mesmo com o usuário bloqueado por senha esquecida, e sempre
  revoga as sessões: quem tinha o refresh antigo não continua dentro.
- **Criação sem senha** (para o próprio usuário definir no primeiro acesso) não é oferecida
  nesta fatia: exigiria canal de entrega de token que o perfil mínimo não tem.

### 1.3 Migração `V22`

`operational_users` ganha `must_change_password boolean not null default false`,
`temporary_password_expires_at timestamptz`, `password_changed_at timestamptz`.
Nenhum backfill além do default: contas existentes continuam válidas.

## Decisão — parte 2: trilha de auditoria

### 2.1 Tabela `audit_log` (`V23`)

```
id uuid pk · occurred_at timestamptz not null · actor_id uuid null
actor_role text null · action text not null · entity_type text not null
entity_id text null · ip_bidx text null · detail jsonb not null default '{}'
prev_hash text not null · hash text not null
```

- **Append-only de verdade:** sem `UPDATE`/`DELETE` pela aplicação; o encadeamento
  `hash = sha256(prev_hash || campos canônicos)` permite detectar adulteração. A verificação
  roda em teste de integração e em um endpoint/rotina de conferência da TI.
- **Índices:** `(occurred_at DESC, id DESC)` para a consulta paginada; `(actor_id,
  occurred_at DESC)`; `(entity_type, entity_id)`. Nenhum índice novo sem `EXPLAIN`.
- **`detail` sem PII:** ids, nomes de campo e valores não sensíveis. Nunca senha, hash de
  senha, token, CPF, coordenada de operador ou dado de vítima. O que for sensível entra como
  `"revealed": true`/`"field": "victim_name"`, não como valor.
- **`ip_bidx`** é o índice cego já usado em `auth_sessions` (`BlindIndexer`), nunca o IP.

### 2.2 Porta e eventos

- Porta pública `audit/api/AuditTrail` (`@NamedInterface("api")`) com
  `record(AuditEvent)`; implementação JDBC na mesma transação do ato auditado. Falha ao
  auditar **derruba a transação** (não existe ato sem trilha).
- Ações mínimas: `USER_CREATED`, `USER_ACTIVATED`, `USER_DEACTIVATED`, `USER_ROLE_CHANGED`,
  `USER_PASSWORD_RESET`, `PASSWORD_CHANGED`, `LOGIN_FAILED` (agregado, sem senha),
  `STATION_ACTIVATED`, `STATION_DEACTIVATED`, `SHIFT_CLOSED_IDLE`, `IMPORT_APPLIED`,
  `UNMAPPED_SIGNAL_DECIDED`, `OCCURRENCE_CLOSED`, `OCCURRENCE_EDITED`, `PERSON_REVEALED`.
- `occurrence_events` continua existindo (histórico da ficha para o operador); a trilha é a
  visão da TI, que atravessa módulos. Não se duplica PII: `PERSON_REVEALED` na trilha leva
  ocorrência + motivo, e o valor revelado fica só no evento da ocorrência.

### 2.3 Consulta

`GET /api/v1/admin/audit?actorId=&action=&entityType=&entityId=&from=&to=&cursor=&limit=`
(`ADMIN`), paginação por cursor (`occurred_at` + `id`), ordenação `DESC`,
`Cache-Control: no-store`. A tela `/admin/auditoria` mostra data, ator, ação, entidade e
detalhe, com filtros e "carregar mais". Exportação fica para depois (RNF06/LGPD: exportação
é decisão de retenção, não de tela).

**Retenção:** `maraberto.audit.retention` (padrão 5 anos, RNF06); o job de expurgo grava a
própria execução na trilha. Nada de apagar por `ON DELETE CASCADE`.

## Alternativas avaliadas

| Alternativa | Por que não |
| :-- | :-- |
| Provedor externo (Keycloak/gov.br) | ADR-009 mantém identidade embutida no perfil mínimo; integração continua opcional |
| Convite por e-mail com link | Sem SMTP no perfil mínimo; exigiria infraestrutura nova só para o primeiro acesso |
| Usuário define a própria senha sem temporária | Precisa de canal seguro para o token; a senha temporária presencial é o caminho mais simples e auditável |
| `DELETE` do usuário | Quebra histórico de plantão/ocorrência e a própria auditoria |
| Auditoria em log de aplicação (`logback`) | Não é consultável, não é imutável e vaza em agregadores; RNF06 pede tabela encadeada |
| Auditoria por `@EventListener(AFTER_COMMIT)` | Perde o ato quando a transação falha depois; a trilha precisa ser síncrona |

## Consequências

- **Positivas:** a TI administra o efetivo de verdade; a coordenação ativa/desativa
  guarda-vidas sem abrir a TI; a pergunta "quem desativou essa conta / revelou essa vítima"
  tem resposta com prova de integridade; a tela de usuários deixa de ser demonstração.
- **Negativas:** mais duas migrações e um módulo novo com escrita em toda ação sensível;
  a senha temporária exige entrega presencial (processo, não tecnologia); Argon2 custa CPU
  por login — já é o custo atual, sem mudança.
- **Segurança:** reset administrativo é operação sensível — exige `ADMIN`, é auditado e
  revoga sessões; a senha temporária nunca é logada nem retornada de novo.
- **Testes:** criar → trocar senha → login; bloqueio de rota com `must_change_password`;
  desativar derruba refresh; reset invalida senha antiga; encadeamento de hash detecta
  adulteração; consulta filtra e pagina; `ModularityTests` verde (`audit` não importa
  módulos de domínio).