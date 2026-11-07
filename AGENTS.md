# AGENTS.md — Memória de contexto do projeto

Este arquivo serve como memória para agentes de IA (assistentes de codificação) que
trabalham neste repositório. Mantenha-o atualizado com o estado do projeto.

## Descrição do projeto

**MarAberto** (repositório: Praias-Seguras) — plataforma open source de segurança balnear:
mapa de zonas de risco, bandeiramento em tempo real, tábua de marés, reportes comunitários
com quórum, estimativa anônima de ocupação e **SOS cidadão** para guarda-vidas.
Ferramenta de *apoio*; não substitui o acionamento oficial (ligar 193).

## Panorama geral (2026-11-07)

- **Semana de início do projeto; 4 issues concluídas** (esqueleto do backend, ambiente local, CI, modelagem espacial)
- **Backend esqueleto funcional:** Spring Boot 3.3.4 (docs visam Boot 4.1.x — atualizar em issue futura), Java 21, Gradle 9.8.2, Virtual Threads, Swagger, `/actuator/health` UP
- **Persistência:** PostgreSQL 16 + PostGIS (H2 removido), Flyway com `V1__init_postgis.sql` + `V2__risk_zone_persistence.sql`, Hibernate Spatial 6.5.3 e `ddl-auto: validate`
- **Domínios em `com.maraberto`** (padrão `api/ application/ domain/ infra/` por módulo): `beaches` (entidades `Beach`, `LifeguardPost`, `MobileBase`, `CriticalZone`), `safety`, `emergency`, `oceanography`, `education`, `shared` — os demais ainda só têm `package-info.java`; sem lógica de negócio/CRUD até agora
- **Módulos potenciais do Architecture.md ainda não criados:** `identity`, `region`, `operations`, `tides` (≈ `oceanography`), `content` (≈ `education`), `occupancy`, `media`, `realtime`, `audit`, `analytics`
- **Stack de infra local (docker compose) validada:** Postgres 16 + PostGIS, Redis 7, RabbitMQ 3, Garage S3 (bucket `media-public`) — a aplicação já conecta ao Postgres; Redis/Garage/RabbitMQ ainda não são usados
- **CI no GitHub Actions** (`.github/workflows/ci.yml`: JDK 21 Temurin + cache Gradle, compilação e testes com Testcontainers PostGIS); **sem frontend**; sem Spring Modulith, sem Spring Security

## Documentação principal (ler antes de trabalhar)

- `docs/Architecture.md` — arquitetura (monólito modular, DDD, Spring Modulith, Postgres+PostGIS, Redis, S3-compatível)
- `docs/Requirements.md` — requisitos funcionais e não funcionais (SOS: p95 < 3 s)
- `docs/Implementation.md` — stack e etapas de implementação
- `docs/adr/` — decisões arquiteturais (ADRs 001–010; mais relevantes: 001 — SOS sem conta, 003 — uma instalação por estado/órgão, 006 — licença MIT, 009 — autenticação embutida, 010 — persistência temporal das zonas de risco)

## Estado do backend

- `backend/src/main/java/com/maraberto/MarAbertoApplication.java` — classe raiz (component scan cobre todos os módulos)
- `application.yml` único em `backend/src/main/resources`; datasource Postgres `jdbc:postgresql://localhost:5432/maraberto` (env `DB_URL`/`DB_USER`/`DB_PASSWORD`), Flyway habilitado e `ddl-auto: validate`
- Migrações `backend/src/main/resources/db/migration/`: `V1__init_postgis.sql` (PostGIS, `beaches`, `lifeguard_posts`, `mobile_bases`, `critical_zones` e índices GiST) e `V2__risk_zone_persistence.sql` (persistência temporal das zonas)
- `critical_zones` tem persistência temporal: `PERMANENT` (estrutural, sem validade) e `SEMI_PERMANENT` (topográfica, com `valid_from`/`valid_until`/`last_confirmed_at`); `EPHEMERAL` fica em `rip_current_markings` (`operations`). Ver ADR-010 e `docs/Architecture.md` §7.8
- Entidades em `com.maraberto.beaches.domain` (JTS `Polygon` para `geometry(Polygon,4326)` e `Point` para `geography(Point,4326)`); `RiskType` cobre todos os riscos, `RiskPersistence` classifica a duração da zona e `FlagColor` fixa as cores de bandeira (tabela de bandeiramento virá na issue de operações)
- Testes: `MarAbertoApplicationTests.contextLoads` e `BeachesSpatialMappingTests` (ambos sobem PostGIS via Testcontainers; base em `com.maraberto.support.PostgisIntegrationTest`)

## CI (GitHub Actions)

- `.github/workflows/ci.yml`, job `build` em `ubuntu-latest`: checkout, JDK 21 (Temurin), `gradle/actions/setup-gradle@v6` com `cache-provider: basic`, `./gradlew compileJava compileTestJava`, `./gradlew test` e upload dos relatórios de teste em caso de falha
- Gatilhos: `pull_request` e `push` para `main`; `defaults.run.working-directory: backend`
- Status check a exigir no ruleset de proteção da `main`: **`CI / build`**
- Ainda não cobre: Testcontainers para Redis/Garage, SAST/SCA, cobertura, lint

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

## Diretrizes de banco, cache e idempotência

- **Views** para projeções de leitura recorrentes; **views materializadas** para agregações/snapshots caros, com índice único + `REFRESH ... CONCURRENTLY` e estratégia de atualização explícita. Não usar view materializada onde o dado precisa ser fresco (SOS, bandeira vigente, postos disponíveis).
- **`FOR UPDATE SKIP LOCKED`** para filas/consumo concorrente (Outbox, escalonamento de SOS, notificações), com transações curtas, índice parcial de status e idempotência.
- **Cache Redis** para leituras quentes (GeoJSON, contexto, bandeira) em cache-aside, chaves versionadas, TTL e invalidação por evento; proteger contra stampede e ter fallback se o Redis cair. Cache nunca é fonte da verdade para SOS/ack/escrita.
- **Idempotência** em escritas com retry/duplo toque: `client_request_id` único, `Idempotency-Key`, constraints únicas/`ON CONFLICT`, consumidores idempotentes e deduplicação no Outbox.
- Medir com `EXPLAIN (ANALYZE, BUFFERS)` antes de adicionar índices, views ou materializações; documentar a consulta e o ganho.
- Detalhes em `docs/Architecture.md` §7.6.

## Roadmap conhecido (próximas entregas plausíveis)

- Spring Modulith + teste `ApplicationModules.verify()`
- `beaches`: repositórios/CRUD, validação `ST_IsValid`, fichas e GeoJSON público
- `beaches`: CRUD de zonas `PERMANENT`/`SEMI_PERMANENT`, revalidação de zonas vencidas e filtro temporal no GeoJSON — ver `docs/Architecture.md` §7.8
- `operations`: `rip_current_markings` para correntes efêmeras/migratórias (RF13), com `valid_until` curto e evento `RipCurrentMarked`
- `beaches`: importador de delimitações via QGIS/KML/GeoJSON (reprojetar para 4326, validar e fazer upsert idempotente) — ver `docs/Architecture.md` §7.7
- `region`: `installation_settings` e Region Pack
- `identity`: autenticação Jwt + refresh rotation + TOTP (seção 13.1 do Architecture.md)
- `emergency` (SOS): Outbox, ack com compare-and-set, escalonamento
- Frontends: PWA banhista + console operacional (pnpm workspace em `frontend/`)
- Pipeline CI: base pronta (build + testes com PostGIS); evoluir com Testcontainers Redis/Garage, SAST/SCA e cobertura

---
Última atualização: 2026-11-07, após execução das 4 primeiras issues.
