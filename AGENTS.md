# AGENTS.md — Contexto Operacional do Projeto MarAberto

> **Regra 0 (Higiene de Contexto):** Este arquivo deve permanecer **curto, direto e estritamente operacional**. **Não** registre histórico de entregas passadas, changelogs ou explicações longas aqui — todo histórico vai para `docs/history/`, especificações para `docs/Requirements.md` e `docs/Architecture.md`, e decisões para `docs/adr/`. **Use sempre o `ai-memory` já configurado no ambiente** para persistir e consultar contexto contínuo de trabalho em vez de inflar este arquivo. Se o código divergir deste documento, **confie no código** e atualize ambos no mesmo PR.

## Visão Geral e Escopo (v1)

- **MarAberto** (`Praias-Seguras`): plataforma open source de segurança balnear multi-instalação (forks por estado). Ferramenta de apoio operacional; **não substitui o 193**.
- **Unidade territorial**: Área de banho (`BEACH`, `RIVER`, `DAM`, `LAGOON`) dividida em `beach_sectors` via importação KML/QGIS (ADR-016, ADR-021). Zero dado regional fixo no código.
- **Fora da v1**: aba `/mares` removida da navegação, do PWA e do app (rota redireciona para o mapa); `features/tides` e o módulo `tides` ficam inativos. RF03/RF31 voltam no **R2**, com provider validado.
- **App Mobile**: WebView Capacitor 8 apontando para `web/` (ADR-019). Pasta `mobile/` (Flutter) congelada.

## Stack e Arquitetura

- **Backend (`backend/`)**: Java 21, Spring Boot 3.3.4, **Spring Modulith 1.2.13** (ADR-011), Virtual Threads, Gradle, PostgreSQL 16 + PostGIS, Flyway (`ddl-auto: validate`, **próxima migration: `V22`**), Redis (opcional no boot, nunca fonte da verdade), RabbitMQ (opcional), S3 (Garage).
  - **Módulos (`com.maraberto.*`)**: `identity`, `region`, `beaches`, `operations`, `emergency`, `occurrences`, `safety`, `tides` (inativo), `content`, `occupancy`, `media`, `realtime`, `audit`, `analytics`, `shared`.
  - **Fronteira estrita**: Módulos importam apenas pacotes `api/` públicos de outros módulos (`ModularityTests`).
- **Frontend (`web/`)**: Vite 8, React 19, TypeScript 6, Tailwind 4, MapLibre GL + PMTiles, STOMP/WebSocket.
  - **Feature-Based (ADR-015)**: `app/`, `shared/`, `features/`. Importação cross-feature apenas via `index.ts` conforme regras do ESLint.

## Regras de Domínio e Telas (Estado Alvo)

- **Operacional (`/operador`)**:
  - **SOS**: Listas (fila aberta e em atendimento) ordenadas **do mais recente para o mais antigo** (`created_at DESC` / `acknowledged_at DESC`) no backend e cliente. Permitir **encerrar atendimento na própria tela do SOS** via `SosCallCloser`.
  - **Plantão e Bases Móveis (ADR-025)**: Bases móveis encerradas saem do seletor; abrir turno em base móvel exige criar nova base na posição atual e desativá-la ao encerrar o plantão. Lease renovado a cada 5 min (`PT15M` timeout).
  - **Ocorrências**: Paginação por cursor (`occurred_at DESC, number DESC`) e filtros; encerramento (`V20`) fecha o SOS na mesma transação.
- **Coordenação (`/gestao`)**:
  - **Home (Mapa de Situação - ADR-026)**: Mapa em tempo real (polling 60 s, ponto único sem histórico de trajeto) com camadas alternáveis (guarda-vidas em plantão, postos/bases ativos, sinalizações vigentes e ocorrências) e lista lateral sincronizada (`flyTo` + abertura da ficha). Se `beaches` estiver vazia, exibir convite para `/gestao/importar`.
  - **Abas**: Importar mapa (`/gestao/importar`), Postos (`/gestao/postos`), CMS Aprender (`/gestao/aprender` — CRUD de vídeos YouTube e materiais de divulgação), Ocorrências e Triagem (`unmapped_signals`).
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

## Backlog Ativo (Fatias 11–15)

| Fatia  | Escopo                                                                                                                                               | Migrations               |
| :----- | :--------------------------------------------------------------------------------------------------------------------------------------------------- | :----------------------- |
| **11** | Home da Coordenação como Mapa de Situação real-time (camadas + lista sincronizada `flyTo`) + aba de postos na gestão (ADR-026)                       | `V23` (se necessário)    |
| **12** | Módulo `content` (CMS Aprender na gestão + tela `/aprender`) + atualização dinâmica de bandeiras/postos no mapa público                              | `V24`                    |
| **13** | Gestão real de usuários (senha temporária obrigatória), blacklist JWT Redis, MFA (TOTP) e consulta de `audit_log` (ADR-027)                          | `V25`, `V26`             |
| **14** | Coleta anônima de amostras de ocupação no mapa público                                                                                               | —                        |
| **15** | Revisão de dual write/idempotência, otimizações SQL, virtual threads, testes k6, SonarQube e docs (`backend/ARCHITECTURE.md`, `web/ARCHITECTURE.md`) | `V27`                    |

## Onde Consultar Detalhes

- `docs/Requirements.md` e `docs/Architecture.md` — requisitos e arquitetura detalhada.
- `docs/adr/001`–`027` — decisões arquiteturais.
- `docs/plan/fatias-10-13-operacao-gestao-admin-ocupacao.md` — diagnóstico das fatias 10–13.
- `docs/history/fatias-1-9-e-endurecimento.md` — histórico das Fatias 1 a 9 (migrações `V1`–`V20`).
- `docs/history/fatias-10.md` — histórico da Fatia 10 (sem migration; `V22` segue livre).
