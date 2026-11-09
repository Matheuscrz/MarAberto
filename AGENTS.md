# AGENTS.md — Memória de contexto do projeto

Este arquivo serve como memória para agentes de IA (assistentes de codificação) que
trabalham neste repositório. Mantenha-o atualizado com o estado do projeto.

## Descrição do projeto

**MarAberto** (repositório: Praias-Seguras) — plataforma open source de segurança balnear:
mapa de zonas de risco, bandeiramento em tempo real, tábua de marés, reportes comunitários
com quórum, estimativa anônima de ocupação e **SOS cidadão** para guarda-vidas.
Ferramenta de *apoio*; não substitui o acionamento oficial (ligar 193).

## Panorama geral (2026-11-08)

- **Entregas concluídas:** esqueleto do backend, ambiente local, CI, modelagem espacial, Etapa 1 arquitetural, camada de sanitização/criptografia (ADR-012), primeira versão do PWA do banhista, home com mapa de tela cheia (MapLibre, 2026-11-09) e publicação da outbox do SOS por STOMP (2026-11-09)
- **Backend esqueleto funcional:** Spring Boot 3.3.4 + Spring Modulith 1.2.13 (ADR-011; docs visam Boot 4.1.x — migrar em issue futura), Java 21, Gradle 9.8.2, Virtual Threads, Swagger, `/actuator/health` UP
- **Persistência:** PostgreSQL 16 + PostGIS (H2 removido), Flyway com `V1__init_postgis.sql` + `V2__risk_zone_persistence.sql` + `V3__emergency_walking_skeleton.sql` + `V4__sensitive_data_protection.sql`, Hibernate Spatial 6.5.3 e `ddl-auto: validate`
- **Domínios em `com.maraberto`** (padrão `api/ application/ domain/ infra/` por módulo): todos os módulos da Architecture.md §6 existem como pacote — `identity`, `region`, `beaches`, `operations`, `emergency`, `safety`, `tides`, `content`, `occupancy`, `media`, `realtime`, `audit`, `analytics`, `shared`. `oceanography` foi renomeado para `tides` e `education` para `content`. Só `beaches` e `emergency` têm entidades/fluxo de domínio; os demais ainda são `package-info.java` (exceto `media` e `realtime`, que têm a porta `RealtimePublisherPort` + `StompRealtimePublisher`)
- **Etapa 1 concluída:** `ApplicationModules.verify()` protege as fronteiras; `media.api.ObjectStoragePort` + `StubObjectStorage`; `realtime.WebSocketConfig` (STOMP em `/ws`); Redis integrado via Spring Data Redis e testado com Testcontainers
- **Stack de infra local (docker compose) validada:** Postgres 16 + PostGIS, Redis 7, RabbitMQ 3, Garage S3 (bucket `media-public`) — a aplicação conecta ao Postgres; Redis é usado no teste de integração; Garage/RabbitMQ ainda não são usados pela aplicação
- **Execução em containers:** `docker compose up -d --build` constrói o backend via `backend/Dockerfile` e o frontend via `web/Dockerfile`; frontend em `localhost:3000`, backend em `localhost:8080`
- **PWA do banhista (2026-11-08):** scaffold do Vite substituído por telas reais em Tailwind 4 (Início, Praias, detalhe da praia, SOS, Marés, Aprender, Sobre, 404), Zustand para preferências não sensíveis, Zod no fluxo do SOS, manifesto + service worker manual + fallback offline, CSP/headers no Nginx e proxy `/api`. Dados de praias/marés ainda são de demonstração e sinalizados na UI.
- **CI no GitHub Actions** (`.github/workflows/ci.yml`: JDK 21 Temurin + cache Gradle, `./gradlew clean check` com Testcontainers PostGIS + Redis); **sem frontend**; sem Spring Security

## Documentação principal (ler antes de trabalhar)

- `docs/Architecture.md` — arquitetura (monólito modular, DDD, Spring Modulith, Postgres+PostGIS, Redis, S3-compatível)
- `docs/Requirements.md` — requisitos funcionais e não funcionais (SOS: p95 < 3 s)
- `docs/Implementation.md` — stack e etapas de implementação
- `docs/adr/` — decisões arquiteturais (ADRs 001–012; mais relevantes: 001 — SOS sem conta, 003 — uma instalação por estado/órgão, 006 — licença MIT, 009 — autenticação embutida, 010 — persistência temporal das zonas de risco, 011 — Modulith 1.2.x com Boot 3.3.x, 012 — sanitização e criptografia de dados sensíveis)

## Estado do backend

- `backend/src/main/java/com/maraberto/MarAbertoApplication.java` — classe raiz (component scan cobre todos os módulos)
- `application.yml` único em `backend/src/main/resources`; datasource Postgres `jdbc:postgresql://localhost:5432/maraberto` (env `DB_URL`/`DB_USER`/`DB_PASSWORD`), Flyway habilitado e `ddl-auto: validate`
- Migrações `backend/src/main/resources/db/migration/`: `V1__init_postgis.sql` (PostGIS, `beaches`, `lifeguard_posts`, `mobile_bases`, `critical_zones` e índices GiST), `V2__risk_zone_persistence.sql` (persistência temporal das zonas), `V3__emergency_walking_skeleton.sql` (SOS), `V4__sensitive_data_protection.sql` (payload da outbox em `TEXT` cifrado e comentários de colunas sensíveis) e `V5__emergency_outbox_publication.sql` (`attempts`/`last_error` com check e comentários)
- `critical_zones` tem persistência temporal: `PERMANENT` (estrutural, sem validade) e `SEMI_PERMANENT` (topográfica, com `valid_from`/`valid_until`/`last_confirmed_at`); `EPHEMERAL` fica em `rip_current_markings` (`operations`). Ver ADR-010 e `docs/Architecture.md` §7.8
- Entidades em `com.maraberto.beaches.domain` (JTS `Polygon` para `geometry(Polygon,4326)` e `Point` para `geography(Point,4326)`); `RiskType` cobre todos os riscos, `RiskPersistence` classifica a duração da zona e `FlagColor` fixa as cores de bandeira (tabela de bandeiramento virá na issue de operações)
- `media.api.ObjectStoragePort` (`presignPut`, `presignGet`, `delete`, `head`) com `media.infra.StubObjectStorage` como bean padrão (`maraberto.storage.provider=stub`); implementação S3 real fica para a Etapa 11
- `realtime.WebSocketConfig` habilita STOMP/WebSocket em `/ws` (broker simples `/topic` e `/queue`, prefixo `/app`); `realtime.api.RealtimePublisherPort` + `StompRealtimePublisher` são a porta de publicação (em memória, at-most-once); fan-out entre instâncias via Redis e Web Push virão na etapa de tempo real
- O walking skeleton do SOS usa `POST /api/v1/emergency-calls`: grava chamada, despacho pendente e evento durável na outbox em uma transação; `client_request_id` é único (UUID validado) para evitar duplicação em retries. O payload da outbox é cifrado com AAD `maraberto:emergency-outbox:v1`. A publicação usa dois gatilhos: listener `@TransactionalEventListener(AFTER_COMMIT)` para latência + sweep `@Scheduled` (`FOR UPDATE SKIP LOCKED`, ISO-8601 `PT5S`) para recuperação; `TransactionTemplate` do publicador exige `REQUIRES_NEW` (com `REQUIRED` o listener AFTER_COMMIT não grava). O frame STOMP é redigido (`callId`/`status`/`occurredAt`; sem coordenadas nem `clientRequestId`) no tópico aberto `/topic/operational.emergency.calls`. Web Push, ack e escalonamento ainda não estão implementados.
- `application.yml` configura `spring.data.redis.*` (`REDIS_HOST`/`REDIS_PORT`/`REDIS_PASSWORD`, timeouts curtos), `maraberto.realtime.allowed-origins`, `maraberto.emergency.outbox.*` (`sweep-interval: PT5S`, `sweep-initial-delay: PT10S`, `batch-size`, `max-attempts` — `@Scheduled` aceita somente ISO-8601), `maraberto.security.crypto.*`, limites de request (`spring.servlet.multipart`, `server.max-http-request-header-size`, `server.tomcat.max-http-form-post-size`, `max-swallow-size`); Redis é opcional no boot e não é fonte da verdade
- Testes: `ModularityTests` (`ApplicationModules.verify()` + módulos esperados), `MarAbertoApplicationTests` (contexto + bean do storage + broker STOMP), `BeachesSpatialMappingTests`, `EmergencyCallIntegrationTests` (criação, idempotência, validação do SOS e payload da outbox cifrado), `EmergencyOutboxPublisherTests` (publicação após commit com payload redigido, recuperação pelo sweep, retry até `max-attempts` → `FAILED`; usa um `RealtimePublisherPort` gravado e sweep em `PT1H` para não fazer corrida), `EmergencyRealtimePublicationTests` (STOMP ponta a ponta com `MappingJackson2MessageConverter`/`JsonNode` — `ByteArrayMessageConverter` falha por só aceitar octet-stream), `ApiSecurityHeadersTests`, testes de crypto/blind index/protetor/CPF/sanitizer, `RedisIntegrationTests` (Redis via Testcontainers) e `ObjectStorageStubTests`; bases em `com.maraberto.support.PostgisIntegrationTest` e `RedisIntegrationTest`

## Segurança de dados (sanitização e criptografia)

Decisão completa em `docs/adr/012-protecao-de-dados-sensiveis-e-sanitizacao.md`; resumo operacional:

- **Sanitização:** `com.maraberto.shared.security.sanitization` — `TextSanitizer` (OWASP Java HTML Sanitizer), `SanitizationPolicy` (`PLAIN_TEXT`, `RICH_TEXT`, `IDENTIFIER`, `EMAIL`, `PHONE`, `CPF`, `URL`, `YOUTUBE_ID`), anotação `@Sanitized` + `SanitizedStringDeserializer`. **Todo DTO de request com texto deve usar `@Sanitized`.** Falha fechada: política inválida vira `""` (Bean Validation reporta); texto livre acima do limite vira 400.
- **Criptografia:** `com.maraberto.shared.security.crypto` — `FieldEncryptor` (AES-256-GCM, IV 12 bytes, tag 128 bits, AAD por contexto, formato `enc:v1:<keyId>:<base64url>`), `BlindIndexer` (HMAC-SHA256 com chave derivada; nunca SHA-256 simples), `SensitiveDataProtector` (fachada + máscaras de CPF/e-mail/telefone/localização/IP), `EncryptedStringConverter` (`@Convert` para colunas String) e `CryptoConfiguration` (valida a chave no boot e recusa a chave de desenvolvimento em `prod`/`production`).
- **Chave mestra:** `CRYPTO_MASTER_KEY` (32 bytes em base64) + `CRYPTO_KEY_ID`; rotação por `previous-keys`. A chave de desenvolvimento em `application.yml`/`docker-compose.yml` é só local. Perder a chave = perder os dados cifrados.
- **Dados sensíveis hoje:** localização precisa do SOS (`emergency_calls.latitude/longitude`, `double precision` por exigência de despacho/geofence, protegida por acesso restrito, backup cifrado, minimização e proibição de log) e payload durável da outbox (cifrado, `TEXT`).
- **Headers da API:** `ApiSecurityHeadersFilter` aplica CSP `default-src 'none'`, `nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy`, `Permissions-Policy`, COOP/CORP e `Cache-Control: no-store` em `/api/v1/emergency-calls` e `/api/v1/auth`.
- **Regra para novos dados:** campo String sensível novo usa `EncryptedStringConverter` ou `SensitiveDataProtector`; nunca gravar PII em claro em outbox, fila, cache, log ou métrica; campos cifrados não aceitam `LIKE`/ordenação/range (igualdade via índice cego).
- **Limites de request:** `spring.servlet.multipart` 5 MB/6 MB, header 16 KB, form post 64 KB, `max-swallow-size` 64 KB.

## Estado do frontend (`web/`)

- Vite 8 + React 19 + TypeScript 6 + Tailwind CSS 4 (tema em `web/src/index.css` via `@theme`, paleta `ocean`/`sand`/`coral`).
- **Home é um mapa de tela cheia** (estilo Google/Apple Maps, RF01): `web/src/pages/HomePage.tsx` monta `web/src/components/map/MapView.tsx` (chunk lazy — MapLibre v6.17.0 tem ~1 MB min; `@types/geojson` como devDependency) com estilo local de demonstração (`web/src/lib/map.ts`: oceano, polígono de costa, retícula de 0,1°, `riskZonesOf`, `accuracyRing`, `FLAG_COLORS`/`RISK_ZONE_COLORS`), marcadores DOM por bandeira, ficha da praia em sheet (`web/src/components/BeachSheet.tsx`), chips de camadas (zonas de risco / marcadores / retícula), botão de centralizar no usuário (geolocalização sob demanda, não persistida), fallback sem WebGL apontando para `/praias` e suporte a PMTiles via `VITE_PMTILES_URL` (ADR-005; protocolo `pmtiles` registrado dinamicamente; o basemap raster substitui a costa demo).
- **SOS flutuante com confirmação** (ADR-001): `web/src/components/SosFab.tsx` — FAB em todas as telas exceto `/sos`; o diálogo confirma antes de navegar ao fluxo real em `/sos` (o envio só acontece lá, com resposta do servidor; "Ligar 193" visível no diálogo).
- **Menu gaveta**: `web/src/components/AppMenu.tsx` (botão de hambúrguer no cabeçalho lista todas as telas + Ligar 193). `Layout.tsx` torna o `main` full-bleed e omite o rodapé somente em `/`; navegação inferior no mobile continua.
- Overlays (menu, diálogo, sheet) usam `web/src/lib/overlay.ts` (`useOverlay`: foco em `[data-autofocus]`, Escape, Tab preso, retorno de foco).
- Rotas em `web/src/App.tsx` (lazy + Suspense): `/`, `/praias`, `/praias/:beachId`, `/sos`, `/mares`, `/aprender`, `/sobre`, `*` (404).
- Estado global em `web/src/store/app-store.ts` (Zustand + `persist`): só preferências não sensíveis (`selectedBeachId`, `installPromptDismissed`). **Nunca persistir coordenadas, `clientRequestId`, CPF, e-mail ou telefone.**
- Validação com Zod em `web/src/lib/validation.ts` (`coordinatesSchema`, `sosSchema`); o backend revalida tudo.
- Sanitização de exibição em `web/src/lib/sanitize.ts` (`sanitizeText`, `sanitizeHtml` com DOMPurify, `safeExternalUrl`, `isValidYoutubeId`). Proibido `dangerouslySetInnerHTML` com dado não confiável.
- SOS em `web/src/pages/SosPage.tsx`: geolocalização com timeout de 10 s, envio com timeout de 5 s (`web/src/lib/api.ts`), `clientRequestId` gerado uma vez e reutilizado em retries, confirmação só após resposta do servidor, “Ligar 193” sempre visível, sem promessa de envio offline, localização não persiste no navegador.
- PWA: `web/public/manifest.webmanifest` (com `launch_handler`), `web/public/sw.js` (`maraberto-shell-v2-map`; manual, versionado; **nunca** cacheia `/api/**`, `/tiles/**`, WebSocket, não-GET ou `Range`), `web/public/offline.html` + `offline.css`, ícones em `web/public/icons/` gerados por `web/scripts/generate-icons.mjs` (`npm run icons`).
- Nginx (`web/nginx.conf`): CSP restritiva (`default-src 'self'`, sem inline/eval, `blob:` apenas em `worker-src`/`img-src` para o worker do MapLibre, `frame-src https://www.youtube-nocookie.com`), headers de segurança, proxy `/api/` para `backend:8080`, cache imutável de `/assets/`, `no-cache` para `index.html`/`sw.js`/manifest.
- Dev: `npm run dev` na porta 3000 com proxy `/api` para `localhost:8080` (`VITE_PROXY_TARGET`); `VITE_API_BASE_URL` é opcional e o padrão é same-origin.
- Dados de praias/marés são **demonstração** (`web/src/data/demo-data.ts`) e sinalizados por `DemoNotice` até existirem endpoints públicos.

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

- `identity`: Spring Security, JWT + refresh rotation + TOTP (seção 13.1 do Architecture.md), usando `@Sanitized` nos DTOs e `EncryptedStringConverter` + índice cego para CPF/e-mail/telefone
- `emergency` (SOS): concluir walking skeleton com publicação WebSocket/Web Push, ack com compare-and-set, escalonamento e validação de latência/resiliência; o frontend já envia o SOS real
- `beaches`: repositórios/CRUD, validação `ST_IsValid`, fichas e GeoJSON público — substitui os dados de demonstração do PWA
- `beaches`: CRUD de zonas `PERMANENT`/`SEMI_PERMANENT`, revalidação de zonas vencidas e filtro temporal no GeoJSON — ver `docs/Architecture.md` §7.8
- `operations`: `rip_current_markings` para correntes efêmeras/migratórias (RF13), com `valid_until` curto e evento `RipCurrentMarked`
- `beaches`: importador de delimitações via QGIS/KML/GeoJSON (reprojetar para 4326, validar e fazer upsert idempotente) — ver `docs/Architecture.md` §7.7
- `region`: `installation_settings` e Region Pack
- `tides`/`content`: endpoints públicos de marés e vídeos; hoje o PWA usa dados de demonstração
- Frontend: console operacional (PWA separado), tiles PMTiles gerados + camadas com dados reais (postos, bandeira vigente, alertas), autenticação com access token em memória e refresh em cookie `HttpOnly`
- Migração para Spring Boot 4.1.x + linha compatível do Spring Modulith (ADR-011)
- Pipeline CI: base pronta (build + testes com PostGIS e Redis); evoluir com build/lint do frontend, Testcontainers Garage, SAST/SCA e cobertura

Validação local mais recente (2026-11-09): `cd backend && ./gradlew clean check` e `cd web && npm run build && npm run lint` concluídos com sucesso.

---
Última atualização: 2026-11-09, após a home com mapa de tela cheia (MapLibre/FAB de SOS/menu gaveta) e a publicação da outbox do SOS por STOMP (listener AFTER_COMMIT + sweep).
