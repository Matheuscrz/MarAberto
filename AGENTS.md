# AGENTS.md — Contexto do Projeto MarAberto

Memória operacional para agentes de IA. **Confie sempre no código** se houver divergência e atualize este arquivo + `docs/` no mesmo PR.

## Visão Geral e Escopo (v1)
- **MarAberto**: Plataforma open source de segurança balnear multi-instalação (forks para CBMs estaduais). Apoio operacional; não substitui o **193**.
- **Unidade territorial**: Área de banho (`BEACH`, `RIVER`, `DAM`, `LAGOON`) dividida em `beach_sectors` via importação KML/QGIS (ADR-016, ADR-021). Zero dependência regional fixa no código.
- **Fora da v1**: Aba `/mares` sai do produto (sem API oficial de marés confiável). Ainda existe no código — rota em `routes.tsx` e item de menu em `RootLayout.tsx` — e sua remoção, junto com o módulo `tides` isolado/inativo, é escopo da Fatia 10.
- **App Mobile**: WebView Capacitor 8 apontando para `web/` (ADR-019). Pasta `mobile/` (Flutter) congelada.

## Arquitetura e Stack
- **Backend (`backend/`)**: Java 21, Spring Boot 3.3.4, **Spring Modulith 1.2.13** (ADR-011), Virtual Threads (`spring.threads.virtual.enabled=true`), Gradle, PostgreSQL 16 + PostGIS, Flyway (`ddl-auto: validate`), Redis, RabbitMQ, S3 (Garage/MinIO).
  - **Módulos (`com.maraberto.*`)**: `identity`, `region`, `beaches`, `operations`, `emergency`, `occurrences`, `safety`, `tides` (inativo), `content` (CMS Aprender), `occupancy`, `media`, `realtime`, `audit`, `analytics`, `shared`.
  - **Fronteira estrita**: Módulos só importam pacotes `api/` públicos de outros módulos (validado por `ModularityTests`).
- **Frontend (`web/`)**: Vite 8, React 19, TypeScript 6, Tailwind 4, MapLibre GL + PMTiles, STOMP over WebSocket.
  - **Feature-Based (ADR-015)**: `app/`, `shared/`, `features/`. Cross-feature apenas via `index.ts` conforme regras do ESLint.

## Regras de Domínio e Telas (Requisitos Atuais)

### 1. Área Operacional (`/operador`)
- **Alertas SOS**: Listagem ordenada estritamente do **mais recente para o mais antigo** (`created_at DESC` / `acknowledged_at DESC`) no backend e cliente. Deve permitir **encerrar o atendimento diretamente na tela de SOS** (fechando o chamado via `SosCallCloser` e exigindo desfecho).
- **Plantão e Bases Móveis (ADR-025)**: Bases móveis antigas/encerradas não voltam ao seletor; ao iniciar plantão em base móvel, exigir criação de uma nova na posição atual do aparelho e encerrá-la ao fim do plantão. Lease do plantão renovado a cada 5 min (`PT15M` timeout).
- **Ocorrências**: Listagem com **paginação por cursor/chave** e filtros apoiada por `VIEW` SQL (sem `LIMIT 100` fixo). Encerramento (`V20`) fecha o SOS na mesma transação.

### 2. Área de Coordenação (`/gestao`)
- **Tela Inicial (Mapa de Situação - ADR-026)**: Mapa em tempo real (polling 60 s / STOMP) exibindo guarda-vidas em plantão ativo, postos fixos e bases móveis ativas, sinalizações vigentes e ocorrências abertas. Controles para ligar/desligar camadas e lista lateral sincronizada onde o clique centraliza o mapa (`flyTo`) e exibe detalhes. Acesso restrito à gestão/operação.
- **Gestão de Postos**: Aba dedicada para CRUD, geolocalização, fotos e status de postos de guarda-vidas.
- **CMS da Aba Aprender (`content`)**: Telas para cadastrar, editar e ordenar dados institucionais de divulgação e materiais educativos (links de vídeos do YouTube, orientações preventivas) exibidos em `/aprender`.
- **Triagem e Mapa**: Aba para fila de sinalizações fora do KML (`unmapped_signals`) e aba de importação KML (`/gestao/importar`).

### 3. Área Administrativa (`/admin`)
- **Gestão de Usuários**: Cadastro, ativação/desativação e reset de senha gerando **senha temporária** com troca obrigatória (`must_change_password = true`) no primeiro login. Sem exclusão física e sem envio de e-mail.
- **Logs e Auditoria**: Tela pesquisável (filtro por `traceId`, usuário, ação, período) consumindo `audit_log` e trilhas de sistema.

### 4. Mapa Geral Público (`/`)
- **Ocupação Anônima (`occupancy`)**: Captura opcional da localização do banhista no cliente (throttled, token efêmero em memória, sem PII) enviada para `/api/v1/occupancy/samples` (`k >= 5` para exibir nível).
- **Atualização em Tempo Real**: Nova sinalização ou posto lançado/alterado publica evento via `RealtimePublisherPort` (STOMP) e atualiza os marcadores no mapa público já carregado sem reload.
- **Aba Aprender (`/aprender`)**: Consome vídeos, links e informações cadastrados pela Coordenação.

## Convenções de Engenharia, Segurança e Performance
- **Consistência e Idempotência**: **Zero dual write** — toda publicação assíncrona (RabbitMQ/STOMP) após escrita no Postgres deve usar **Transactional Outbox** (`AFTER_COMMIT` + sweeper). Idempotência obrigatória via constraints `UNIQUE` / `client_request_id` (SOS, ACK, fechamento, ocupação, KML).
- **Banco de Dados (`V22` é a próxima migration)**:
  - Usar `FOR UPDATE SKIP LOCKED` em workers (outbox e escalonamento).
  - Usar `VIEW`s para listagens operacionais, `MATERIALIZED VIEW`s (`REFRESH CONCURRENTLY`) ou triggers para agregações de mapa/ocupação, e cache Redis (opcional no boot, nunca fonte da verdade) para leituras públicas.
  - Distâncias em metros via `geography`; polígonos em `geometry(...,4326)`.
- **Virtual Threads**: Evitar blocos `synchronized` em I/O (usar `ReentrantLock`) para não causar pinning.
- **Segurança & LGPD (ADR-012)**:
  - Todo campo texto em DTO de request usa `@Sanitized` (`PLAIN_TEXT`, `URL`, `YOUTUBE_ID`, `CPF`, etc.).
  - PII (CPF de operacionais, dados de vítima) cifrado com AES-256-GCM (`FieldEncryptor`) + HMAC (`BlindIndexer`). **Nunca** gravar PII em logs, outbox ou `localStorage`/`sessionStorage`.
  - **Auth**: Access token só em memória; refresh em cookie `HttpOnly; Secure; SameSite=Strict`. Blacklist de JWT em Redis no logout/desativação/reset. Rate limit por IP + lockout progressivo por conta (`V21`) + **MFA (TOTP)** para perfis administrativos.
  - **STOMP**: Allowlist estrita no `SUBSCRIBE` e bloqueio de `SEND` do cliente para tópicos/filas.
  - **Erros HTTP**: Sempre RFC 9457 (`application/problem+json`).
- **Observabilidade**: Logs estruturados com **`traceId`** propagado via MDC/Micrometer em HTTP, STOMP, RabbitMQ e `@Scheduled`. `/actuator/health` e `/actuator/info` são públicos; o resto do `/actuator/**` exige `ADMIN`, e o Swagger/api-docs fica **desligado em `prod`**.
- **Frontend/MapLibre**: Nunca aplicar `transform`, `scale` ou `transition` no elemento raiz do marcador do MapLibre (aplicar em elemento filho). Overlays acessíveis via `useOverlay` (`<dialog open>`, `<output>`).
- **Qualidade e Docs**: Zero bugs/vulnerabilidades/code smells críticos no **SonarQube**; testes de carga (k6) validando capacidade máxima da API; documentação detalhada da arquitetura e guia de fork estadual em `backend/ARCHITECTURE.md` e `web/ARCHITECTURE.md`.
- **Git**: Commits em **pt-BR** (Conventional Commits); classes em inglês; **nunca commitar sem aval explícito**.

## Backlog de Execução (Fatias 10–15)

| Fatia | Escopo | Migrations |
| :--- | :--- | :--- |
| **10** | Ordem SOS `DESC`, encerrar SOS na tela operacional, base móvel efêmera por plantão (ADR-025), paginação + view SQL em ocorrências e remoção de `/mares` | `V22` |
| **11** | Home da Coordenação como Mapa de Situação real-time (camadas toggle + lista sincronizada `flyTo`) + aba de postos na gestão (ADR-026) | `V23` |
| **12** | Módulo `content` (CMS na gestão para divulgação e vídeos/links da aba Aprender) + eventos real-time no mapa público para novas bandeiras/postos | `V24` |
| **13** | Gestão real de usuários (ativar/desativar, senha temporária obrigatória), blacklist JWT Redis, MFA (TOTP) e tela de logs/auditoria com `traceId` (ADR-027) | `V25`, `V26` |
| **14** | Coleta anônima de posição de banhistas no cliente para cálculo de ocupação das praias | — |
| **15** | Revisão de dual write/idempotência, otimizações SQL (views materializadas, triggers, `SKIP LOCKED`), virtual threads, testes de carga (k6), 0 issues SonarQube e docs (`backend/ARCHITECTURE.md`, `web/ARCHITECTURE.md`) | `V27` |

## Referências Rápidas
- `docs/Architecture.md` e `docs/Requirements.md` — especificações completas.
- `docs/adr/001` a `027` — decisões arquiteturais (ler 012, 013, 016, 018, 022–027).
- `docs/plan/fatias-10-13-operacao-gestao-admin-ocupacao.md` — diagnóstico técnico das fatias.
- `docs/history/` — o que as fatias 1–9 entregaram e o roadmap registrado em 2026-11-15; histórico, não estado atual.