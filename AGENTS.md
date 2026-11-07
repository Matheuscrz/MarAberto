# AGENTS.md — Memória de contexto do projeto

Este arquivo serve como memória para agentes de IA (assistentes de codificação) que
trabalham neste repositório. Mantenha-o atualizado com o estado do projeto.

## Descrição do projeto

**MarAberto** (repositório: Praias-Seguras) — plataforma open source de segurança balnear:
mapa de zonas de risco, bandeiramento em tempo real, tábua de marés, reportes comunitários
com quórum, estimativa anônima de ocupação e **SOS cidadão** para guarda-vidas.
Ferramenta de *apoio*; não substitui o acionamento oficial (ligar 193).

## Panorama geral (2026-11-07)

- **Semana de início do projeto; 5 issues concluídas** (esqueleto do backend, ambiente local, CI, modelagem espacial, Etapa 1 arquitetural)
- **Backend esqueleto funcional:** Spring Boot 3.3.4 + Spring Modulith 1.2.13 (ADR-011; docs visam Boot 4.1.x — migrar em issue futura), Java 21, Gradle 9.8.2, Virtual Threads, Swagger, `/actuator/health` UP
- **Persistência:** PostgreSQL 16 + PostGIS (H2 removido), Flyway com `V1__init_postgis.sql` + `V2__risk_zone_persistence.sql` + `V3__emergency_walking_skeleton.sql`, Hibernate Spatial 6.5.3 e `ddl-auto: validate`
- **Domínios em `com.maraberto`** (padrão `api/ application/ domain/ infra/` por módulo): todos os módulos da Architecture.md §6 existem como pacote — `identity`, `region`, `beaches`, `operations`, `emergency`, `safety`, `tides`, `content`, `occupancy`, `media`, `realtime`, `audit`, `analytics`, `shared`. `oceanography` foi renomeado para `tides` e `education` para `content`. Só `beaches` tem entidades de domínio; os demais ainda são `package-info.java` (exceto `media` e `realtime`)
- **Etapa 1 concluída:** `ApplicationModules.verify()` protege as fronteiras; `media.api.ObjectStoragePort` + `StubObjectStorage`; `realtime.WebSocketConfig` (STOMP em `/ws`); Redis integrado via Spring Data Redis e testado com Testcontainers
- **Stack de infra local (docker compose) validada:** Postgres 16 + PostGIS, Redis 7, RabbitMQ 3, Garage S3 (bucket `media-public`) — a aplicação conecta ao Postgres; Redis é usado no teste de integração; Garage/RabbitMQ ainda não são usados pela aplicação
- **Execução em containers:** `docker compose up -d --build` constrói o backend via `backend/Dockerfile` e o frontend via `web/Dockerfile`; frontend em `localhost:3000`, backend em `localhost:8080`
- **Ponto de parada do frontend (2026-11-07):** o app Vite/React está compilando com Tailwind via `web/src/index.css`, é servido pelo Nginx no container e ainda exibe o scaffold inicial do Vite. Próximos passos: substituir o scaffold pelas telas do MarAberto com Tailwind e adicionar manifesto/service worker/offline para PWA.
- **CI no GitHub Actions** (`.github/workflows/ci.yml`: JDK 21 Temurin + cache Gradle, `./gradlew clean check` com Testcontainers PostGIS + Redis); **sem frontend**; sem Spring Security

## Documentação principal (ler antes de trabalhar)

- `docs/Architecture.md` — arquitetura (monólito modular, DDD, Spring Modulith, Postgres+PostGIS, Redis, S3-compatível)
- `docs/Requirements.md` — requisitos funcionais e não funcionais (SOS: p95 < 3 s)
- `docs/Implementation.md` — stack e etapas de implementação
- `docs/adr/` — decisões arquiteturais (ADRs 001–011; mais relevantes: 001 — SOS sem conta, 003 — uma instalação por estado/órgão, 006 — licença MIT, 009 — autenticação embutida, 010 — persistência temporal das zonas de risco, 011 — Modulith 1.2.x com Boot 3.3.x)

## Estado do backend

- `backend/src/main/java/com/maraberto/MarAbertoApplication.java` — classe raiz (component scan cobre todos os módulos)
- `application.yml` único em `backend/src/main/resources`; datasource Postgres `jdbc:postgresql://localhost:5432/maraberto` (env `DB_URL`/`DB_USER`/`DB_PASSWORD`), Flyway habilitado e `ddl-auto: validate`
- Migrações `backend/src/main/resources/db/migration/`: `V1__init_postgis.sql` (PostGIS, `beaches`, `lifeguard_posts`, `mobile_bases`, `critical_zones` e índices GiST) e `V2__risk_zone_persistence.sql` (persistência temporal das zonas)
- `critical_zones` tem persistência temporal: `PERMANENT` (estrutural, sem validade) e `SEMI_PERMANENT` (topográfica, com `valid_from`/`valid_until`/`last_confirmed_at`); `EPHEMERAL` fica em `rip_current_markings` (`operations`). Ver ADR-010 e `docs/Architecture.md` §7.8
- Entidades em `com.maraberto.beaches.domain` (JTS `Polygon` para `geometry(Polygon,4326)` e `Point` para `geography(Point,4326)`); `RiskType` cobre todos os riscos, `RiskPersistence` classifica a duração da zona e `FlagColor` fixa as cores de bandeira (tabela de bandeiramento virá na issue de operações)
- `media.api.ObjectStoragePort` (`presignPut`, `presignGet`, `delete`, `head`) com `media.infra.StubObjectStorage` como bean padrão (`maraberto.storage.provider=stub`); implementação S3 real fica para a Etapa 11
- `realtime.WebSocketConfig` habilita STOMP/WebSocket em `/ws` (broker simples `/topic` e `/queue`, prefixo `/app`); fan-out entre instâncias via Redis e Web Push virão na etapa de tempo real
- O walking skeleton do SOS iniciou com `POST /api/v1/emergency-calls`: grava chamada, despacho pendente e evento durável na outbox em uma transação; `client_request_id` é único para evitar duplicação em retries. Notificação, ack e frontend ainda não estão implementados.
- `application.yml` configura `spring.data.redis.*` (`REDIS_HOST`/`REDIS_PORT`/`REDIS_PASSWORD`, timeouts curtos) e `maraberto.realtime.allowed-origins`; Redis é opcional no boot e não é fonte da verdade
- Testes: `ModularityTests` (`ApplicationModules.verify()` + módulos esperados), `MarAbertoApplicationTests` (contexto + bean do storage + broker STOMP), `BeachesSpatialMappingTests`, `EmergencyCallIntegrationTests` (criação, idempotência e validação do SOS), `RedisIntegrationTests` (Redis via Testcontainers) e `ObjectStorageStubTests`; bases em `com.maraberto.support.PostgisIntegrationTest` e `RedisIntegrationTest`

## CI (GitHub Actions)

- `.github/workflows/ci.yml`, job `build` em `ubuntu-latest`: checkout, JDK 21 (Temurin), `gradle/actions/setup-gradle@v6` com `cache-provider: basic`, `./gradlew compileJava compileTestJava`, `./gradlew clean check` e upload dos relatórios de teste em caso de falha
- Gatilhos: `pull_request` e `push` para `main`; `defaults.run.working-directory: backend`
- Status check a exigir no ruleset de proteção da `main`: **`CI / build`**
- Ainda não cobre: Testcontainers para Garage, SAST/SCA, cobertura, lint

## Ambiente local (docker compose)

- `docker-compose.yml` na raiz; `cp .env.example .env` para credenciais (padrão de usuários/senhas: `maraberto`)
- Backend usa `DB_URL`/`DB_USER`/`DB_PASSWORD` (defaults `jdbc:postgresql://localhost:5432/maraberto`, `maraberto`, `maraberto`), compatíveis com o serviço `postgres` do Compose
- **Garage no lugar de MinIO** (ADR-006): imagem MinIO não é mais pública; S3 em `localhost:9000`, admin em `localhost:9001`; config em `deploy/garage/garage.toml`
- Volumes nomeados: `maraberto_postgres_data`, `maraberto_redis_data`, `maraberto_garage_data`, `maraberto_rabbitmq_data`
- Swagger: `backend` em `/swagger-ui/index.html` (com a app rodando)

## Convenções

- Commits em português (pt-BR) com Conventional Commits (`feat:`, `chore:`, `fix:`, `docs:`).
- Não comentar código desnecessariamente; seguir estilo do código existente.
- Idioma do código/commits: pt-BR quando for texto exibido ao usuário; nomes de classes em inglês.
- APIs HTTP devem documentar no Swagger/OpenAPI as respostas de sucesso e os erros esperados, incluindo o schema do corpo quando aplicável.
- Erros HTTP devem seguir RFC 9457 com `Content-Type: application/problem+json` e os campos `type`, `title`, `status`, `detail` e `instance`. O `type` é uma URI estável para identificar/documentar o problema, não um redirecionamento; `instance` identifica a ocorrência da requisição. Não expor stack trace, credenciais ou dados pessoais.
- O contrato OpenAPI de erros deve usar `ApiProblem`, com `errors` como lista de `FieldValidationError` quando houver falhas de validação. Os tipos RFC estáveis do módulo de emergência são `https://maraberto.org/problems/invalid-request` e `https://maraberto.org/problems/malformed-json`; o frontend deve tratar essas URIs como identificadores de erro, sem assumir redirecionamento HTTP.
- Ao construir `ProblemDetail`, sempre definir explicitamente `setTitle`, `setType` e `setInstance`, além de um `detail` seguro e acionável. Records de request/response/evento e contratos de infraestrutura devem receber `@Schema` com descrições e exemplos quando forem parte do contrato documentado.

## Diretrizes de banco, cache e idempotência

- **Views** para projeções de leitura recorrentes; **views materializadas** para agregações/snapshots caros, com índice único + `REFRESH ... CONCURRENTLY` e estratégia de atualização explícita. Não usar view materializada onde o dado precisa ser fresco (SOS, bandeira vigente, postos disponíveis).
- **`FOR UPDATE SKIP LOCKED`** para filas/consumo concorrente (Outbox, escalonamento de SOS, notificações), com transações curtas, índice parcial de status e idempotência.
- **Cache Redis** para leituras quentes (GeoJSON, contexto, bandeira) em cache-aside, chaves versionadas, TTL e invalidação por evento; proteger contra stampede e ter fallback se o Redis cair. Cache nunca é fonte da verdade para SOS/ack/escrita.
- **Idempotência** em escritas com retry/duplo toque: `client_request_id` único, `Idempotency-Key`, constraints únicas/`ON CONFLICT`, consumidores idempotentes e deduplicação no Outbox.
- Medir com `EXPLAIN (ANALYZE, BUFFERS)` antes de adicionar índices, views ou materializações; documentar a consulta e o ganho.
- Detalhes em `docs/Architecture.md` §7.6.

## Roadmap conhecido (próximas entregas plausíveis)

- `identity`: Spring Security, JWT + refresh rotation + TOTP (seção 13.1 do Architecture.md)
- `emergency` (SOS): concluir walking skeleton com publicação WebSocket/Web Push, ack com compare-and-set, escalonamento, frontend e validação de latência/resiliência
- `beaches`: repositórios/CRUD, validação `ST_IsValid`, fichas e GeoJSON público
- `beaches`: CRUD de zonas `PERMANENT`/`SEMI_PERMANENT`, revalidação de zonas vencidas e filtro temporal no GeoJSON — ver `docs/Architecture.md` §7.8
- `operations`: `rip_current_markings` para correntes efêmeras/migratórias (RF13), com `valid_until` curto e evento `RipCurrentMarked`
- `beaches`: importador de delimitações via QGIS/KML/GeoJSON (reprojetar para 4326, validar e fazer upsert idempotente) — ver `docs/Architecture.md` §7.7
- `region`: `installation_settings` e Region Pack
- Frontends: PWA banhista + console operacional (pnpm workspace em `frontend/`)
- Frontend atual em `web/`: scaffold Vite/React com Tailwind já compilando; telas reais, PWA, service worker, manifesto e offline parcial ficam para a próxima sessão
- Migração para Spring Boot 4.1.x + linha compatível do Spring Modulith (ADR-011)
- Pipeline CI: base pronta (build + testes com PostGIS e Redis); evoluir com Testcontainers Garage, SAST/SCA e cobertura

Validação local mais recente: `cd backend && ./gradlew clean check` concluído com sucesso em 2026-11-07.

---
Última atualização: 2026-11-07, após padronização RFC 9457, documentação OpenAPI dos records e contratos de erro.
