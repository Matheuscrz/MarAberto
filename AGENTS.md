# AGENTS.md — Contexto Operacional do Projeto MarAberto

> **Regra 0 (Higiene de Contexto):** Este arquivo deve permanecer **curto, direto e estritamente operacional**. **Não** registre histórico de entregas passadas, changelogs ou explicações longas aqui — todo histórico vai para `docs/history/`, especificações para `docs/Requirements.md` e `docs/Architecture.md`, e decisões para `docs/adr/`. **Use sempre o `ai-memory` já configurado no ambiente** para persistir e consultar contexto contínuo de trabalho em vez de inflar este arquivo. Se o código divergir deste documento, **confie no código** e atualize ambos no mesmo PR.

## Visão Geral e Escopo (v1)

- **MarAberto** (`Praias-Seguras`): plataforma open source de segurança balnear multi-instalação (forks por estado). Ferramenta de apoio operacional; **não substitui o 193**.
- **Unidade territorial**: Área de banho (`BEACH`, `RIVER`, `DAM`, `LAGOON`) dividida em `beach_sectors` via importação KML/QGIS (ADR-016, ADR-021). Zero dado regional fixo no código.
- **Fora da v1**: aba `/mares` removida da navegação, do PWA e do app (rota redireciona para o mapa); `features/tides` e o módulo `tides` ficam inativos. RF03/RF31 voltam no **R2**, com provider validado.
- **App Mobile**: WebView Capacitor 8 apontando para `web/` (ADR-019). Pasta `mobile/` (Flutter) congelada.

## Stack e Arquitetura

- **Backend (`backend/`)**: Java 21, Spring Boot 3.3.4, **Spring Modulith 1.2.13** (ADR-011), Virtual Threads, Gradle, PostgreSQL 16 + PostGIS, Flyway (`ddl-auto: validate`, **próxima migration: `V26`**), Redis (opcional no boot, nunca fonte da verdade), RabbitMQ (opcional), S3 (Garage).
  - **Módulos (`com.maraberto.*`)**: `identity`, `region`, `beaches`, `operations`, `emergency`, `occurrences`, `safety`, `tides` (inativo), `content`, `occupancy`, `media`, `realtime`, `audit`, `analytics`, `shared`.
  - **Fronteira estrita**: Módulos importam apenas pacotes `api/` públicos de outros módulos (`ModularityTests`).
- **Frontend (`web/`)**: Vite 8, React 19, TypeScript 6, Tailwind 4, MapLibre GL + PMTiles, STOMP/WebSocket.
  - **Feature-Based (ADR-015)**: `app/`, `shared/`, `features/`. Importação cross-feature apenas via `index.ts` conforme regras do ESLint.

## Regras de Domínio e Telas (Estado Alvo)

- **Operacional (`/operador`)**:
  - **SOS**: Listas (fila aberta e em atendimento) ordenadas **do mais recente para o mais antigo** (`created_at DESC` / `acknowledged_at DESC`) no backend e cliente. Permitir **encerrar atendimento na própria tela do SOS** via `SosCallCloser`.
  - **Plantão e Bases Móveis (ADR-028, substitui a ADR-025)**: O plantão pode ser aberto **sem estrutura** (posição do aparelho obrigatória, raio padrão no SOS) ou vinculado a um posto fixo/base móvel **ativa**. A base móvel é estrutura própria, cadastrada com nome e fotos, aparece no mapa enquanto ativa e **não é desativada ao encerrar o turno**. Lease renovado a cada 60 s (`PT15M` timeout), só com a aba visível.
  - **Ocorrências**: Paginação por cursor (`occurred_at DESC, number DESC`) e filtros de situação, CIOSP e período (`from`/`to`) aplicados no servidor; encerramento (`V20`) fecha o SOS na mesma transação.
- **Coordenação (`/gestao`)**:
  - **Home (Mapa de Situação - ADR-026)**: Mapa de tela cheia com camadas alternáveis **em memória** (`useState`, sem storage): guarda-vidas em plantão, postos/bases ativos, sinalizações vigentes, **áreas não mapeadas** (alertas de sinalização em triagem, ADR-029) e ocorrências ativas/CIOSP pendente. Todo pin e cada item da lista lateral são clicáveis (`flyTo` + ficha `StationSheet`/`SignalSheet`/`UnmappedSignalSheet`/`OccurrenceSheet`). Equipe consultada a cada 15 s; posição do plantão (ponto único, sem histórico) a cada 60 s. Se `beaches` estiver vazia, exibir convite para `/gestao/importar`.
  - **Equipe (`/gestao/equipe`)**: dados reais de `GET /api/v1/management/team` (`COORDINATOR`/`ADMIN`, `Cache-Control: no-store`), com posição/`stale`, lease e contadores do dia. Encerrar o plantão remove o guarda-vidas da lista e do mapa.
  - **Abas**: Importar mapa (`/gestao/importar`), Equipe, Postos (`/gestao/postos` — leitura e ativação/desativação de estruturas via `/api/v1/operations/stations`, com abas Ativos/Inativos/Todos, tipo, busca e paginação por cursor), CMS Aprender (`/gestao/aprender` — CRUD de vídeos YouTube e materiais de divulgação), Ocorrências e Triagem (`unmapped_signals`, com link para o ponto exato no Google Maps).
- **Administração (`/admin`)**:
  - **Usuários (ADR-027)**: CRUD sem exclusão física, ativação/desativação e reset gerando **senha temporária** com troca obrigatória (`must_change_password = true`) no primeiro login.
  - **Logs e Auditoria**: Tela pesquisável (`traceId`, ator, ação, período) sobre `audit_log` append-only.
- **Público (`/`)**:
  - **Ocupação (`occupancy`)**: Envio anônimo da posição do banhista após consentimento explícito na UI (1 amostra por janela de 15 min em memória, `k >= 5` para exibir nível).
  - **Mapa dinâmico**: Novas sinalizações e postos atualizam o mapa público já aberto sem reload.
  - **Aprender (`/aprender`)**: Exibe vídeos (facade `youtube-nocookie.com` sem iframe antes do clique) e informações cadastradas pela Coordenação.

## Convenções de Engenharia, Segurança e Qualidade

- **Consistência e Idempotência**: Zero _dual write_ — publicações assíncronas usam Transactional Outbox (`AFTER_COMMIT` + sweeper). Idempotência obrigatória via constraints `UNIQUE` / `client_request_id`.
- **Banco e Virtual Threads**: `FOR UPDATE SKIP LOCKED` em filas/jobs; `VIEW`s para leituras operacionais e `MATERIALIZED VIEW` (`REFRESH CONCURRENTLY`) para agregações de mapa; distâncias sempre em metros via `geography`. Evitar `synchronized` em I/O (preferir `ReentrantLock`).
- **Segurança & LGPD (ADR-012)**:
  - `@Sanitized` obrigatório em todo campo texto de DTO de request.
  - PII (CPF, dados de vítima) cifrado com AES-256-GCM + HMAC (`BlindIndexer`). **Nunca** gravar PII ou coordenadas de operador em logs, outbox, métricas ou `localStorage`/`sessionStorage`.
  - Access token só em memória; refresh em cookie `HttpOnly; Secure; SameSite=Strict`; errors em RFC 9457 (`application/problem+json`).
- **Observabilidade**: Logs estruturados com `traceId` (MDC/Micrometer). `/actuator/health` e `/actuator/info` públicos; demais `/actuator/**` exigem `ADMIN`; Swagger desligado em `prod`.
- **Frontend/MapLibre**: Nunca aplicar `transform`, `scale` ou `transition` no elemento raiz do marcador MapLibre (usar elemento filho). Overlays acessíveis via `useOverlay` (`<dialog open>`, `<output>`).
- **Git & Qualidade**: Commits em **pt-BR** (Conventional Commits); classes em inglês; **nunca commitar sem aval explícito**. Manter `./gradlew clean check`, `npm run lint` e `npm run build` verdes e zero issues críticas no SonarQube.

## Backlog Ativo (Fatias 12–15)

| Fatia  | Escopo                                                                                                                                               | Migrations               |
| :----- | :--------------------------------------------------------------------------------------------------------------------------------------------------- | :----------------------- |
| **12** | ✅ Atualização dinâmica de bandeiras/postos no mapa público (entregue em 2026-11-25, sem migration). Pendente: módulo `content` (CMS Aprender na gestão + tela `/aprender`) | `V27` |
| **13** | ✅ Usuários reais (senha temporária obrigatória) e trilha de auditoria consultável (ADR-027) — entregues em 2026-11-24 (`V24`, `V25`). Pendente: blacklist JWT Redis e MFA (TOTP) | `V26`                    |
| **14** | Coleta anônima de amostras de ocupação no mapa público                                                                                               | —                        |
| **15** | Revisão de dual write/idempotência, otimizações SQL, virtual threads, testes k6, SonarQube e docs (`backend/ARCHITECTURE.md`, `web/ARCHITECTURE.md`) | —                        |

## Onde Consultar Detalhes

- `docs/Requirements.md` e `docs/Architecture.md` — requisitos e arquitetura detalhada.
- `docs/adr/001`–`029` — decisões arquiteturais.
- `docs/plan/fatias-10-13-operacao-gestao-admin-ocupacao.md` — diagnóstico das fatias 10–13.
- `docs/history/fatias-1-9-e-endurecimento.md` — histórico das Fatias 1 a 9 (migrações `V1`–`V20`).
- `docs/history/fatias-10.md` — histórico da Fatia 10 (sem migration; `V21` e `V22` vieram depois).
- `docs/history/fatias-11.md` — histórico da Fatia 11 (mapa de situação, equipe real e aba de postos; sem migration).
- `docs/history/correcoes-pos-fatia-11.md` — correções pós-Fatia 11 (equipe, ficha no mapa, mídia, filtros/paginação e Google Maps; migration `V22`).
- `docs/history/alertas-e-plantao-livre.md` — alerta de área não mapeada no mapa (público e gestão) e plantão sem estrutura/base móvel autônoma; migration `V23` (ADR-028/ADR-029).
- `docs/history/administracao-de-identidade-e-auditoria.md` — administração real de usuários com senha temporária e trilha `audit_log` consultável; migrations `V24`/`V25` (ADR-027).
- `docs/history/atualizacao-dinamica-do-mapa-publico.md` — eventos `FlagChanged`/`PostChanged`, cache público de 10 s e atualização do mapa já carregado sem F5 (sem migration).
