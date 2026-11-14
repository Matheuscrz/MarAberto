# Fatias 10 a 13 — console de campo, situação da coordenação, administração da TI e ocupação pública

**Status:** backlog detalhado; **nenhum item desta lista está implementado**.
**Data:** 2026-11-13, sobre o commit `80856c6`.
**Relacionado:** ADR-009 (identidade), ADR-012 (dados sensíveis), ADR-013 (ocorrências),
ADR-020 (escala e persistência do caminho crítico), ADR-022/ADR-024 (estruturas e
sinalização), ADR-025/ADR-026/ADR-027 (propostas nesta rodada), RF10, RF11, RF15, RF20,
RF35, RF40, RN06, RN15, RN17, RNF04, RNF06.

Este documento nasce dos problemas reais observados em uso, não de uma lista de desejos:
cada seção aponta **onde o código está hoje** e **o que precisa mudar** para a tela fazer o
que o operador e a coordenação esperam. A ordem das fatias é a ordem sugerida de trabalho;
cada uma vira **uma branch** (nomes na seção 5).

---

## 0. Diagnóstico verificado no código (2026-11-13)

| # | Sintoma relatado | Onde está hoje | O que falta |
| :-- | :-- | :-- | :-- |
| 1 | "A lista em atendimento começa da antiga para a mais nova" | `DispatchService.OPEN_CALLS_SQL` e `ASSIGNED_CALLS_SQL` usam `ORDER BY c.created_at` e `ORDER BY c.acknowledged_at` (ASC) | Ordenar `DESC` (empate por `id DESC`) no servidor e fixar em teste |
| 2 | "Não tem como fechar o atendimento, fica sempre aberto" | O encerramento existe em `/operador/ocorrencias` (`POST /occurrences/{id}/close`); o card "Em atendimento por você" do `OperatorSosPage` só mostra um texto mandando ir para a outra tela | Ação de encerrar **na própria tela do SOS**, reutilizando `OccurrenceSheet`/`closeOccurrence` |
| 3 | "Bases móveis antigas aparecem para selecionar" | `ShiftService.TARGETS_SQL` lista **toda** base `active`; a base só sai do mapa quando o plantão termina, mas continua `active` e volta ao seletor | Ciclo de vida da base móvel (ADR-025): desativar ao encerrar o plantão |
| 4 | "Lista de ocorrências do guarda-vidas fica extensa demais" | `GET /api/v1/occurrences` aceita `status` e `limit` (padrão 50, teto 100), sem página/cursor; a tela pede 100 fixo | Paginação por cursor (chave `occurred_at` + `number`) e filtros (RF35) |
| 5 | "Listagem da equipe é boa, mas falta mapa" | `ManagementTeamPage` usa `INITIAL_TEAM` local (mock) com posição, contadores e `Power` | API real de plantões + mapa com camadas e clique centralizando |
| 6 | "A tela inicial da gestão precisa ser mapa/dashboard" | Rota índice de `/gestao` é `ImportMapPage` | Dashboard de situação como home, importação como aba |
| 7 | "Falta aba de postos na gestão" | `/gestao` tem importar, equipe, ocorrências e triagem; postos estão em `/admin/postos` | Aba de postos (leitura + ativação pela coordenação) |
| 8 | "Gestão de usuários da TI é mock" | `AdminUsersPage` usa `features/admin/api/demo-users.ts`; `identity` só tem `/auth/*` | Endpoints de administração de usuários (ADR-027) |
| 9 | "Falta visualização de logs" | Módulo `audit` é só `package-info`; não existe tabela `audit_log` | Trilha append-only + consulta paginada na TI (RNF06) |
| 10 | "Calcular ocupação com a localização dos banhistas" | `recordOccupancySample()` existe em `web/src/features/beaches/api/occupancy.ts` e **nunca é chamado**; a ocupação é sempre `INSUFFICIENT` | Enviar a amostra anônima com consentimento, uma vez por janela |
| 11 | "Registro manual de ocorrência (193/rádio)" | Continua rascunho local no frontend | Fora destas fatias (RF33); fica no roadmap |

Nada aqui depende de troca de banco ou de broker. As decisões de escala continuam as da
**ADR-020**: paginação e retenção antes de particionar, e nenhum índice novo sem
`EXPLAIN (ANALYZE, BUFFERS)`.

---

## 1. Fatia 10 — console de campo: SOS e ocorrências

**Branch:** `30-fix-console-de-campo-sos-e-ocorrencias`
**Migração:** nenhuma (o índice de `occurrences` já atende à paginação por
`responder_id`; se o `EXPLAIN` mostrar *sort* caro, entra `V21` só com o índice).

### 1.1 Ordem das listas do console do SOS

- **Problema:** as duas consultas do `DispatchService` ordenam do mais antigo para o mais
  novo. O chamado que acabou de chegar aparece no fim da fila — o oposto do que a operação
  precisa quando o alerta sonoro toca.
- **Decisão:** `ORDER BY c.created_at DESC, c.id DESC` (fila aberta) e
  `ORDER BY c.acknowledged_at DESC, c.id DESC` (em atendimento). A ordenação é do servidor;
  o cliente **não** reordena (mesma regra já aplicada à sinalização).
- **Aceite:** teste de integração em `DispatchConsoleTests`/`ShiftControllerTests` que abre
  dois chamados e verifica a ordem nas duas listas; o teste não depende de relógio do
  cliente.

### 1.2 Encerrar o atendimento sem sair da tela do SOS

- **Problema:** o card "Em atendimento por você" manda o operador para
  `/operador/ocorrencias`. Em campo, isso é uma tela a mais no meio de um atendimento, e o
  usuário lê como "não tem como fechar".
- **Decisão:** o card do chamado assumido ganha a ação **"Encerrar atendimento"**, que abre
  a `OccurrenceSheet` já existente (buscando a ocorrência por `emergencyCallId`) e, ao
  concluir, recarrega a lista do SOS. `closeOccurrence` continua sendo a única chamada de
  escrita — sem novo endpoint.
- **Regra:** o chamado só sai da lista quando o servidor fecha o chamado
  (`SosCallCloser`), nunca por otimismo do cliente.
- **Aceite:** encerrar pela tela do SOS remove o card e o chamado do `assigned` em até um
  ciclo de polling (10 s) — sem F5; encerrar pela tela de ocorrências continua funcionando;
  sem ocorrência pareada (dado legado), a ação mostra uma mensagem explicando e não trava.

### 1.3 Ciclo de vida da base móvel (ADR-025)

- **Problema:** base móvel é criada com `active=true` e nunca é aposentada. O seletor de
  plantão lista bases de meses atrás; escolher uma delas coloca o plantão em um ponto que
  não corresponde a lugar nenhum — e a distância do SOS é medida por ele.
- **Decisão proposta (ADR-025):** a base móvel pertence ao plantão. Ao encerrar o plantão
  (manual, logout ou inatividade), a base usada é **desativada** (`active=false`,
  `deactivated_at`, `status_note` com o motivo) **se nenhum outro plantão aberto a usa**.
  Abrir plantão em base móvel passa a ser "cadastrar a base na posição atual" (fluxo da
  `V16`/RF39), e não escolher uma base antiga de uma lista.
- **Nada é apagado:** `shift_sessions.mobile_base_id` e ocorrências continuam apontando para
  a linha; a estrutura apenas sai do mapa e do seletor.
- **Lista de campo:** bases desativadas há mais de N dias deixam de poluir o console
  (agrupadas em "estruturas fora do mapa", com filtro por período). O console continua
  mostrando **todas** as estruturas — a decisão é de exibição, não de permissão (ADR-024).
- **Aceite:** após encerrar o plantão, a base não aparece em
  `GET /api/v1/dispatch/shifts/targets`; `GET /api/v1/operations/stations` ainda a devolve;
  outro plantão aberto na mesma base impede a desativação; `ModularityTests` verde.

### 1.4 Ocorrências: paginação, filtros e views

- **Problema:** a lista do guarda-vidas cresce para sempre. Hoje o cliente pede
  `limit=100` e recebe a mesma primeira página eternamente; a coordenação não tem filtro
  por período.
- **Decisão:** paginação **por cursor** (keyset), não `OFFSET`:
  - `GET /api/v1/occurrences?status=&beforeOccurredAt=&beforeNumber=&limit=` devolve
    `items` + `nextCursor` (`occurredAt` + `number` do último item) e `hasMore`.
  - Ordem fixa `occurred_at DESC, number DESC` (já é a atual); o cursor usa a mesma chave do
    índice `idx_occ_responder`.
  - `limit` padrão 20, teto 100; `status` continua opcional; a coordenação ganha
    `from`/`to` (período) na mesma consulta.
  - `number` é o número interno da ocorrência (`occurrences.number`), não o protocolo do
    CIOSP.
- **Views:** a lista continua lendo a tabela `occurrences` (não há ganho em criar view
  materializada com filtro por escopo e cursor). Se a coordenação passar a pedir contadores
  por período, aí entra **uma view** `occurrence_daily_summary` — com `REFRESH` no mesmo
  padrão de `beach_map_features`, nunca por consulta. Registrar a decisão no PR.
- **ADR-020:** a tabela `occurrences` só vira gargalo de manutenção (item 4 da ADR-020). O
  gatilho continua sendo **> 10 M linhas** ou backup/restore acima de 30 min; a paginação
  não justifica particionar. Medir com `EXPLAIN` antes de criar índice novo.
- **Aceite:** duas páginas consecutivas não repetem nem pulam ocorrência; encerrar uma
  ocorrência no meio da paginação não desloca a página seguinte; `hasMore=false` na última;
  teste de contrato no backend e a lista do operador com "Carregar mais".

### 1.5 Fora desta fatia

Registro manual (`POST /occurrences`, `FIELD_ATTENDED`), busca por protocolo do CIOSP
(RF37) e anexos continuam no roadmap — a paginação é pré-requisito, não a entrega.

---

## 2. Fatia 11 — painel de situação da coordenação

**Branch:** `31-feature-painel-de-situacao-da-coordenacao`
**Migração:** nenhuma (posição já vive em `shift_sessions.last_position` /
`last_position_at` / `last_position_accuracy_m`).
**Decisão de privacidade:** ADR-026.

### 2.1 Equipe com dados reais

- **Problema:** `ManagementTeamPage` é `INITIAL_TEAM` em memória; `toggleStatus` só muda
  estado local. Números, posição e plantão não vêm do servidor.
- **Decisão:** endpoint autenticado de coordenação
  `GET /api/v1/management/team` devolvendo, por plantão aberto: operador (nome/matrícula),
  estrutura (posto/base), praia/setor, início do plantão, última posição (lat/lon, precisão,
  `at`, `stale`), contadores do dia (ocorrências, atendimentos em campo, sinalizações,
  CIOSP pendente) e se o lease está vivo.
- **Escopo:** `beach_sectors` do escopo da coordenação (mesma regra do painel atual); a
  posição só sai nessa rota (RN17).
- **Aceite:** lista reflete plantão aberto/fechado na hora (30 s de polling); operador sem
  posição não quebra a tela; posição vencida aparece como "sem sinal há X min"; encerrar o
  plantão remove a linha.

### 2.2 Mapa de situação (ADR-026)

- **Objetivo:** a home da coordenação é um mapa de tela cheia com camadas ligáveis:
  - **guarda-vidas em plantão** (posição do plantão, cor por status do lease);
  - **postos fixos e bases móveis ativos**;
  - **sinalizações vigentes**;
  - **ocorrências** em atendimento/pendentes de CIOSP.
- **Interação:** todo objeto é clicável; o clique **centraliza** o mapa e abre a ficha já
  existente (`StationSheet`, `SignalSheet`, `OccurrenceSheet`). Nada de ficha nova.
- **Camadas:** `useState` em memória (sem `localStorage`, como o resto do app); a escolha
  vale para a sessão. A lista lateral de equipe acompanha os pins: clicar num nome centraliza
  no guarda-vidas.
- **Posição "em tempo real":** o console de plantão passa a enviar a posição a cada **60 s**
  enquanto o turno está aberto e a aba visível (`PUT /dispatch/shifts/current/position` já
  existe). O servidor guarda **um** ponto por plantão — sem histórico de trajeto, sem
  tabela de telemetria (RN17 + ADR-020). O mapa só mostra plantão com lease vivo.
- **Não fazer:** streaming de posição por WebSocket nesta fatia. A ADR-020 já registra que
  o broker é para entrega do SOS; posição de painel é polling e pode ir para Redis/STOMP
  quando houver medição que justifique.
- **Aceite:** as quatro camadas ligam/desligam sem recarregar; clique centraliza e abre a
  ficha; plantão encerrado some do mapa em até 60 s; nenhuma coordenada de operador aparece
  em log, cache, métrica ou endpoint público; `Cache-Control: no-store` nos endpoints da
  coordenação.

### 2.3 Home da gestão e aba de postos

- **Home:** `/gestao` (índice) passa a ser o mapa de situação; **Importar mapa** continua
  sendo a primeira aba do menu. Numa instalação sem mapa (tabela `beaches` vazia), a home
  mostra o convite para importar em vez de um mapa vazio — o primeiro passo continua sendo a
  importação.
- **Postos:** nova aba `/gestao/postos` com a leitura de `GET /api/v1/operations/stations`
  (todas as estruturas) e a ativação/desativação que hoje só existe em `/admin/postos`,
  agora para `COORDINATOR` (RN11). A TI continua com a visão administrativa.
- **Navegação:** importar, mapa (home), equipe/postos, ocorrências, triagem.
- **Aceite:** coordenação ativa/desativa estrutura pela própria área; mapa vazio explica o
  primeiro passo; menu e rotas cobertos por teste de render.

---

## 3. Fatia 12 — administração da TI: identidade e auditoria

**Branch:** `32-feature-administracao-de-identidade-e-auditoria`
**Migrações:** `V22` (usuário: senha temporária e troca obrigatória) e `V23` (`audit_log`).
**Decisão:** ADR-027.

### 3.1 Usuários reais, com ativação e desativação

- **Problema:** `AdminUsersPage` é mock (`demo-users.ts`) e o módulo `identity` só expõe
  `/auth/*`. Não há como a TI criar, desativar ou corrigir papel de ninguém.
- **Decisão:** endpoints em `/api/v1/admin/users` (`ADMIN`):
  - `GET` lista paginada (nome, matrícula, e-mail mascarado, papel, `active`,
    `lastAccessAt`);
  - `POST` cria o operador com **senha temporária** gerada no servidor, exibida **uma vez**
    ao administrador (nunca por e-mail — não há SMTP no perfil mínimo);
  - `PATCH /{id}` altera papel e `active`; desativar **revoga as sessões** (`auth_sessions`)
    e impede login;
  - `POST /{id}/password-reset` gera nova senha temporária e revoga sessões;
  - **Nunca** `DELETE`: histórico de plantão/ocorrência aponta para o usuário (RNF06).
- **`must_change_password`:** login com senha temporária só devolve sessão suficiente para
  `POST /auth/password` (troca); qualquer outra rota responde `403
  password-change-required`. A senha definitiva passa a valer e o flag é limpo na mesma
  transação.
- **Senha:** Argon2 (como hoje), política mínima configurável e recusa das senhas óbvias;
  a temporária expira em 24 h e não pode ser reutilizada.
- **Aceite:** criar → login com temporária → troca obrigatória → login normal; desativar
  derruba o refresh e o access em uso; reset invalida a senha anterior; papel novo vale no
  próximo login; tudo auditado.

### 3.2 Trilha de auditoria consultável (RNF06)

- **Problema:** o módulo `audit` é só `package-info`; as decisões de triagem e a revelação
  de vítima gravam eventos próprios, mas não há trilha única consultável pela TI.
- **Decisão:** `audit_log` append-only (`id`, `occurred_at`, `actor_id`, `actor_role`,
  `action`, `entity_type`, `entity_id`, `ip_bidx`, `detail JSONB`, `prev_hash`, `hash`),
  escrita **na mesma transação** do ato auditado por uma porta `audit/api/AuditTrail`
  (`@NamedInterface("api")`). Ações: login/ falha de login (agregada, sem senha), usuário
  criado/ativado/desativado/papel alterado/reset de senha, estrutura ativada/desativada,
  importação aplicada, ocorrência encerrada/editada, revelação de pessoa, decisão de
  triagem, plantão encerrado por inatividade.
- **Sem PII em claro:** `detail` guarda ids, nomes de campo e valores não sensíveis; nunca
  senha, token, coordenada de operador, CPF ou dado de vítima.
- **Consulta:** `GET /api/v1/admin/audit?actorId=&action=&entityType=&from=&to=&cursor=`
  (`ADMIN`), paginação por cursor e `Cache-Control: no-store`. A tela `/admin/auditoria`
  mostra data, ator, ação, entidade e detalhe, com filtros.
- **Retenção:** configurável (`maraberto.audit.retention`), padrão da RNF06 (5 anos); expurgo
  por job com registro do próprio expurgo.
- **Aceite:** cada ação da lista grava exatamente uma linha; `prev_hash`/`hash` encadeiam
  (alterar linha antiga quebra a verificação); consulta filtra e pagina; usuário sem `ADMIN`
  recebe 403; teste de integração cobre o encadeamento.

---

## 4. Fatia 13 — ocupação anônima no mapa público

**Branch:** `33-feature-ocupacao-anonima-no-mapa-publico`
**Migração:** nenhuma (`V14` já tem `occupancy_samples`).

### 4.1 O problema real

A ocupação está implementada de ponta a ponta **menos no cliente**: `recordOccupancySample`
nunca é chamada, então não existe amostra e toda praia responde `INSUFFICIENT`. O servidor
está correto (RN06, RNF04, `V14`) e não deve ser afrouxado.

### 4.2 O que "obter a localização dos banhistas" significa (e não significa)

Não é possível — nem desejável — ler a localização de outros usuários. O que existe é
**cada cliente contribuindo com a própria posição de forma anônima**: o servidor resolve o
setor por `ST_Contains`, grava `beach_id`/`sector_id`/janela/HMAC do token e **descarta a
coordenada**. Nenhum identificador persistente, nenhum histórico, nenhum IP.

### 4.3 Decisão de UX e consentimento

- A amostra é enviada **depois de o usuário conceder a localização** na própria interface
  (botão "Usar minha localização" já existente no mapa/lista e na ficha), nunca com prompt
  automático na abertura — prompt sem contexto é negado e queima a permissão do app.
- No máximo **uma amostra por janela de 15 min** por cliente (o servidor já deduplica por
  token + setor + janela, mas o cliente não deve fazer requisição inútil): guardar em
  memória do módulo `{sectorId, bucketStart}`; o token rotaciona ao recarregar.
- Aba escondida não envia; ao voltar, envia se venceu a janela. Falha é silenciosa.
- Na ficha da praia, o card de ocupação explica o que é e mostra "dados insuficientes" com
  a amostra mínima `k=5` — sem inventar número (RN06).
- Aviso de privacidade curto no primeiro uso da localização (RNF04/LGPD).

### 4.4 Aceite

- Com posição concedida dentro de setor mapeado, `POST /occupancy/samples` acontece uma vez
  e a ficha passa a mostrar estimativa quando houver `k ≥ 5`;
- Duas recargas na mesma janela não inflam a contagem;
- Fora de área mapeada: `204`, nenhuma linha gravada, nenhum erro na tela;
- Negar a localização mantém a navegação intacta e a ocupação em "dados insuficientes";
- Teste de integração do servidor já cobre o HMAC/janela; o frontend ganha teste da regra
  "uma amostra por janela" (sem `localStorage`).

---

## 5. Ordem de trabalho, branches e migrações

| Ordem | Branch | Fatia | Migração |
| :-- | :-- | :-- | :-- |
| 1 | `30-fix-console-de-campo-sos-e-ocorrencias` | 10 — ordem do SOS, encerrar no console, base móvel, paginação | — (ou `V21` só com índice medido) |
| 2 | `31-feature-painel-de-situacao-da-coordenacao` | 11 — equipe real, mapa de situação, home, aba de postos | — |
| 3 | `32-feature-administracao-de-identidade-e-auditoria` | 12 — usuários, senha temporária, auditoria | `V22`, `V23` |
| 4 | `33-feature-ocupacao-anonima-no-mapa-publico` | 13 — amostra anônima no cliente | — |

Regras para todas:

- **Branch a partir de `main`**, depois do merge da branch atual
  (`29-feature-implementacao-de-sinalizacao-e-postos-de-guarda-vidas`). Não empilhar uma
  fatia sobre a outra: cada PR parte do `main` atualizado.
- Commit em pt-BR (Conventional Commits), um assunto por commit; **não commitar sem aval**.
- `./gradlew clean check` (hoje 195 testes), `npm run lint` e `npm run build` verdes antes
  de abrir o PR.
- Documento e código no mesmo PR: `Requirements.md`, `Architecture.md` (§7.9, §7.11, §7.12,
  §8.x, §9, §13.2) e `AGENTS.md`.
- Erros em RFC 9457 (`application/problem+json`), `@Sanitized` em todo texto de request,
  OpenAPI com `@Schema` quando o campo for contrato.
- Fronteiras do Modulith: nada de importar de outro módulo fora da `api/` pública. O painel
  da coordenação lê por portas (`operations/api`, `safety/api`, `occurrences/api`), não por
  SQL atravessando módulos.
- **ADR-020:** medir antes de indexar/particionar; paginação por cursor não é gatilho de
  particionamento; posição de painel é polling, não stream.