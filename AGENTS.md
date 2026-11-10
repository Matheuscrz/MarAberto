# AGENTS.md — Memória de contexto do projeto

Este arquivo serve como memória para agentes de IA (assistentes de codificação) que
trabalham neste repositório. Mantenha-o atualizado com o estado do projeto.

## Descrição do projeto

**MarAberto** (repositório: Praias-Seguras) — plataforma open source de segurança balnear:
mapa de zonas de risco, bandeiramento em tempo real, tábua de marés, reportes comunitários
com quórum, estimativa anônima de ocupação e **SOS cidadão** para guarda-vidas.
Ferramenta de *apoio*; não substitui o acionamento oficial (ligar 193).

## Panorama geral (2026-11-10)

- **Direção atual (2026-11-10):** universalização (qualquer CBM/estado), **operação de campo** (o guarda-vidas registra ocorrências atendidas que não passaram pelo 193/SOS e cadastra postos/bases com a localização atual), **acompanhamento pela coordenação** (posição/plantão dos operadores, nº de ocorrências, sinalizações e pendências), **ocorrências operacionais** com ficha para transcrever no CIOSP (ADR-013), **login único com três áreas por perfil** (`/operador`, `/gestao`, `/admin`), sessão com cookie de refresh `HttpOnly; Secure; SameSite=Strict` e access token em memória, **frontend feature-based** (ADR-015), **busca tolerante a erros com `pg_trgm`** (ADR-014) e **importação de mapeamento QGIS/KML com setores de praia** (ADR-016). No backend, `identity` já entrega **login/refresh/logout/me reais** com RBAC (Spring Security + JWT) e `occurrences` continua planejamento; o frontend usa o backend real, com acesso demo só se `VITE_DEMO_AUTH=true` em desenvolvimento.
- **Entregas concluídas:** esqueleto do backend, ambiente local, CI, modelagem espacial, Etapa 1 arquitetural, camada de sanitização/criptografia (ADR-012), primeira versão do PWA do banhista, home com mapa de tela cheia (MapLibre, 2026-11-09), publicação da outbox do SOS por STOMP (2026-11-09), **login único com RBAC nas três áreas (2026-11-10)** e frontend *feature-based* com operador/gestão/TI
- **Backend esqueleto funcional:** Spring Boot 3.3.4 + Spring Modulith 1.2.13 (ADR-011; docs visam Boot 4.1.x — migrar em issue futura), Java 21, Gradle 9.8.2, Virtual Threads, Swagger, `/actuator/health` UP
- **Persistência:** PostgreSQL 16 + PostGIS (H2 removido), Flyway com `V1__init_postgis.sql` + `V2__risk_zone_persistence.sql` + `V3__emergency_walking_skeleton.sql` + `V4__sensitive_data_protection.sql` + `V5__emergency_outbox_publication.sql` + `V6__identity_operational_users.sql` + `V7__identity_auth_sessions.sql`, Hibernate Spatial 6.5.3 e `ddl-auto: validate`
- **Domínios em `com.maraberto`** (padrão `api/ application/ domain/ infra/` por módulo): todos os módulos da Architecture.md §6 existem como pacote — `identity`, `region`, `beaches`, `operations`, `emergency`, `safety`, `tides`, `content`, `occupancy`, `media`, `realtime`, `audit`, `analytics`, `shared`. `oceanography` foi renomeado para `tides` e `education` para `content`. Têm fluxo de domínio: `beaches`, `emergency` e **`identity`** (`OperationalUser` com e-mail/CPF por índice cego e matrícula, `Role` = `LIFEGUARD`/`COORDINATOR`/`ADMIN`, `AuthenticationService` com login por e-mail/CPF/matrícula, `RefreshTokenService` com rotação e detecção de reuso, `JwtService`, `SecurityConfig`, `AuthController` em `/api/v1/auth`, seed de desenvolvimento opt-in e migrações `V6`/`V7`). Os demais são `package-info.java` (exceto `media` e `realtime`, que têm a porta `RealtimePublisherPort` + `StompRealtimePublisher`).
- **Etapa 1 concluída:** `ApplicationModules.verify()` protege as fronteiras; `media.api.ObjectStoragePort` + `StubObjectStorage`; `realtime.WebSocketConfig` (STOMP em `/ws`); Redis integrado via Spring Data Redis e testado com Testcontainers
- **Stack de infra local (docker compose) validada:** Postgres 16 + PostGIS, Redis 7, RabbitMQ 3, Garage S3 (bucket `media-public`) — a aplicação conecta ao Postgres; Redis é usado no teste de integração; Garage/RabbitMQ ainda não são usados pela aplicação
- **Execução em containers:** `docker compose up -d --build` constrói o backend via `backend/Dockerfile` e o frontend via `web/Dockerfile`; frontend em `localhost:3000`, backend em `localhost:8080`
- **PWA do banhista (2026-11-08):** scaffold do Vite substituído por telas reais em Tailwind 4 (Início, Praias, detalhe da praia, SOS, Marés, Aprender, Sobre, 404), Zustand para preferências não sensíveis, Zod no fluxo do SOS, manifesto + service worker manual + fallback offline, CSP/headers no Nginx e proxy `/api`. Dados de praias/marés ainda são de demonstração e sinalizados na UI.
- **CI no GitHub Actions** (`.github/workflows/ci.yml`: JDK 21 Temurin + cache Gradle, `./gradlew clean check` com Testcontainers PostGIS + Redis); sem lint/build do frontend no CI

## Documentação principal (ler antes de trabalhar)

- `docs/Architecture.md` — arquitetura (monólito modular, DDD, Spring Modulith, Postgres+PostGIS, Redis, S3-compatível)
- `docs/Requirements.md` — requisitos funcionais e não funcionais (SOS: p95 < 3 s)
- `docs/Implementation.md` — stack e etapas de implementação
- `docs/adr/` — decisões arquiteturais (ADRs 001–017; mais relevantes: 001 — SOS sem conta, 003 — uma instalação por estado/órgão, 006 — licença MIT, 009 — autenticação embutida, 010 — persistência temporal das zonas de risco, 011 — Modulith 1.2.x com Boot 3.3.x, 012 — sanitização e criptografia de dados sensíveis, 013 — ocorrências operacionais, 014 — busca com `pg_trgm` antes de OpenSearch, 015 — frontend feature-based, 016 — importação de mapeamento QGIS/KML com setores, 017 — geoindexação do SOS)

## Estado do backend

- `backend/src/main/java/com/maraberto/MarAbertoApplication.java` — classe raiz (component scan cobre todos os módulos)
- `application.yml` único em `backend/src/main/resources`; datasource Postgres `jdbc:postgresql://localhost:5432/maraberto` (env `DB_URL`/`DB_USER`/`DB_PASSWORD`), Flyway habilitado e `ddl-auto: validate`
- Migrações `backend/src/main/resources/db/migration/`: `V1__init_postgis.sql` (PostGIS, `beaches`, `lifeguard_posts`, `mobile_bases`, `critical_zones` e índices GiST), `V2__risk_zone_persistence.sql` (persistência temporal das zonas), `V3__emergency_walking_skeleton.sql` (SOS), `V4__sensitive_data_protection.sql` (payload da outbox em `TEXT` cifrado e comentários de colunas sensíveis) e `V5__emergency_outbox_publication.sql` (`attempts`/`last_error` com check e comentários)
- `critical_zones` tem persistência temporal: `PERMANENT` (estrutural, sem validade) e `SEMI_PERMANENT` (topográfica, com `valid_from`/`valid_until`/`last_confirmed_at`); `EPHEMERAL` fica em `rip_current_markings` (`operations`). Ver ADR-010 e `docs/Architecture.md` §7.8
- Entidades em `com.maraberto.beaches.domain` (JTS `Polygon` para `geometry(Polygon,4326)` e `Point` para `geography(Point,4326)`); `RiskType` cobre todos os riscos, `RiskPersistence` classifica a duração da zona e `FlagColor` fixa as cores de bandeira (tabela de bandeiramento virá na issue de operações)
- `media.api.ObjectStoragePort` (`presignPut`, `presignGet`, `delete`, `head`) com `media.infra.StubObjectStorage` como bean padrão (`maraberto.storage.provider=stub`); implementação S3 real fica para a Etapa 11
- `realtime.WebSocketConfig` habilita STOMP/WebSocket em `/ws` (broker simples `/topic` e `/queue`, prefixo `/app`); `realtime.api.RealtimePublisherPort` + `StompRealtimePublisher` são a porta de publicação (em memória, at-most-once); fan-out entre instâncias via Redis e Web Push virão na etapa de tempo real
- **`identity` (login real):** `AuthController` expõe `POST /api/v1/auth/login|refresh|logout` e `GET /api/v1/auth/me`; login aceita e-mail, CPF (índice cego) ou matrícula e devolve access token JWT curto (`PT15M`) no corpo + refresh opaco (32 bytes, hash SHA-256 no banco) no cookie `__Secure-maraberto_rt; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Strict`. `RefreshTokenService` faz rotação por família de sessão (`auth_sessions`), com janela de tolerância de 10 s entre abas e revogação da família em reuso fora da janela; `AuthenticationService.refresh` **não** é `@Transactional` para garantir o commit da revogação antes do 401. `SecurityConfig` (Spring Security stateless + `JwtAuthenticationFilter`) libera login/refresh/logout e Swagger, exige Bearer em `/me` e nas áreas por papel (`LIFEGUARD`/`COORDINATOR`/`ADMIN`), com 401/403 em `application/problem+json`. Seed de desenvolvimento opt-in (`maraberto.identity.dev-seed.enabled`/`IDENTITY_DEV_SEED`, recusado em `prod`) cria `operador@maraberto.dev` (LIFEGUARD, `BM-10492`, CPF `11144477735`), `coordenador@maraberto.dev` (COORDINATOR, `CBM-2041`, CPF `52998224725`) e `admin@maraberto.dev` (ADMIN, sem matrícula, CPF `39053344705`), todos com senha `demo1234`; o login aceita e-mail, CPF ou matrícula e o seed é idempotente por e-mail. Segredo JWT em `JWT_SECRET` (padrão de dev recusado no perfil `prod`).
- O walking skeleton do SOS usa `POST /api/v1/emergency-calls`: grava chamada, despacho pendente e evento durável na outbox em uma transação; `client_request_id` é único (UUID validado) para evitar duplicação em retries. O payload da outbox é cifrado com AAD `maraberto:emergency-outbox:v1`. A publicação usa dois gatilhos: listener `@TransactionalEventListener(AFTER_COMMIT)` para latência + sweep `@Scheduled` (`FOR UPDATE SKIP LOCKED`, ISO-8601 `PT5S`) para recuperação; `TransactionTemplate` do publicador exige `REQUIRES_NEW` (com `REQUIRED` o listener AFTER_COMMIT não grava). O frame STOMP é redigido (`callId`/`status`/`occurredAt`; sem coordenadas nem `clientRequestId`) no tópico aberto `/topic/operational.emergency.calls`. Web Push, ack e escalonamento ainda não estão implementados.
- **Geoindexação do SOS:** a posição fica em `emergency_calls.latitude/longitude` (`double precision`, ADR-012) e o ponto geográfico é uma coluna **gerada** (`ST_SetSRID(ST_MakePoint(longitude, latitude),4326)::geography`) com índice **GiST**; a escolha de postos usa `ST_DWithin` sobre `geography` + KNN `<->`, a geofence usa `ST_Contains` no polígono da praia (GiST da V1) e a fila de escalonamento continua em B-tree parcial. A geofence **não bloqueia** o chamado (só marca `outside_area` para triagem). Detalhes, DDL e medição em `docs/Architecture.md` §7.12; a coluna/índice entram junto da consulta de postos elegíveis (ainda não implementada).
- `application.yml` configura `spring.data.redis.*` (`REDIS_HOST`/`REDIS_PORT`/`REDIS_PASSWORD`, timeouts curtos), `maraberto.realtime.allowed-origins`, `maraberto.emergency.outbox.*` (`sweep-interval: PT5S`, `sweep-initial-delay: PT10S`, `batch-size`, `max-attempts` — `@Scheduled` aceita somente ISO-8601), `maraberto.security.crypto.*`, `maraberto.security.jwt.*` (`JWT_SECRET`, issuer, `PT15M`/`P7D`), `maraberto.identity.dev-seed.enabled`, limites de request (`spring.servlet.multipart`, `server.max-http-request-header-size`, `server.tomcat.max-http-form-post-size`, `max-swallow-size`); Redis é opcional no boot e não é fonte da verdade
- Testes: `ModularityTests` (`ApplicationModules.verify()` + módulos esperados), `MarAbertoApplicationTests` (contexto + bean do storage + broker STOMP), `BeachesSpatialMappingTests`, `EmergencyCallIntegrationTests` (criação, idempotência, validação do SOS e payload da outbox cifrado), `EmergencyOutboxPublisherTests` (publicação após commit com payload redigido, recuperação pelo sweep, retry até `max-attempts` → `FAILED`; usa um `RealtimePublisherPort` gravado e sweep em `PT1H` para não fazer corrida), `EmergencyRealtimePublicationTests` (STOMP ponta a ponta com `MappingJackson2MessageConverter`/`JsonNode` — `ByteArrayMessageConverter` falha por só aceitar octet-stream), `ApiSecurityHeadersTests`, `IdentityAuthIntegrationTests` (login por e-mail/CPF/matrícula, refresh rotativo, `/me`, logout e 401/403 por papel), `RefreshTokenReuseTests` (detecção de reuso e revogação de família), testes de crypto/blind index/protetor/CPF/sanitizer, `RedisIntegrationTests` (Redis via Testcontainers) e `ObjectStorageStubTests`; bases em `com.maraberto.support.PostgisIntegrationTest` e `RedisIntegrationTest`

## Ocorrências, perfis e operação de campo (decisões de 2026-11-09/10)

- **Ocorrência ≠ SOS.** O SOS (`emergency`) é o alerta em tempo real; a **ocorrência** (`occurrences`, ainda não implementada) é a ficha operacional. Todo SOS gera uma ocorrência **por evento** (`EmergencyCallCreated`, idempotente por `emergency_call_id UNIQUE`) com job de reconciliação; **`emergency` nunca importa `occurrences`** e o alerta nunca espera a ocorrência.
- **Origem:** `SOS_CITIZEN`, `CBM_193`, `EXTERNAL_OTHER` e **`FIELD_ATTENDED`**. `FIELD_ATTENDED` é o atendimento que o guarda-vidas fez **sem** chamado no 193 ou no SOS (situação vista na praia, rádio, presencial, outro posto) e que ele registra depois para não perder o histórico. Campos mínimos em Requirements RN12; dados de pessoas são opcionais, **cifrados e mascarados**; duplicata só avisa, nunca bloqueia (RN13).
- **Registro pelo operador em campo:** o formulário é curto, usável com uma mão, com data/hora **do fato**, natureza, praia/setor + referência (ou coordenada), resumo e desfecho. O operador pode registrar **ao encerrar o atendimento**; a ocorrência entra na fila do CIOSP conforme a configuração. `client_request_id` garante idempotência em retry/duplo toque.
- **Posto/base em campo:** o guarda-vidas que trabalha no local pode cadastrar/atualizar posto fixo e base móvel, usando a **localização atual do aparelho** (`position_source` = `MANUAL`/`DEVICE_GPS`/`MAP_PICK` + `position_accuracy_m`), com ajuste do pino e foto da estrutura. A posição do dispositivo é usada **só no cadastro**, não vira rastreamento contínuo. A TI também mantém o cadastro.
- **Painel da coordenação (`/gestao`):** acompanhamento geral do campo — quem está de plantão, **última posição conhecida** de cada operador (com horário e acurácia), nº de ocorrências por operador/praia/setor, sinalizações lançadas, pendências no CIOSP e alertas ativos. A coordenação ativa/desativa guarda-vidas do seu escopo; **não cria usuário**. Posição de operador é dado sensível: visível só ao escopo autorizado, nunca em listagem pública, log, cache ou métrica.
- **Área da TI (`/admin`):** **só cadastro/convite de usuários, papéis, ativação e poder administrativo sobre tudo** (auditado). **Não** exibir roadmap, status de integração, "pendente (backend)" nem planejamento de desenvolvimento nessa área; pendência de produto fica em `docs/`/issue, não na tela do órgão.
- **Perfis e áreas:** um `/login`; `LIFEGUARD` → `/operador`, `COORDINATOR` → `/gestao`, `ADMIN` (TI) → `/admin`. Guarda de rota no cliente é só UX; **a autorização é do servidor** (papel + escopo de posto/praia/setor).
- **CIOSP:** sem integração (ADR-007). O sistema monta a **ficha de transcrição** e controla `ciosp_status` (`PENDING`/`REGISTERED`/`NOT_REQUIRED`); campos e ordem vêm de configuração da instalação. Pendente com o CBM: lista oficial de campos.
- **Sessão:** access token JWT curto **só em memória**; refresh opaco em cookie `__Secure-maraberto_rt; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Strict` (`__Host-` não serve: exige `Path=/`). **Proibido** token em `localStorage`/`sessionStorage`. Renovação *single-flight* entre abas (`navigator.locks`) + janela de tolerância de ~10 s no servidor, senão duas abas disparam a detecção de reuso. `next` do login só aceita caminho interno.
- **Busca:** `SearchPort` com adaptador PostgreSQL (`pg_trgm`, normalização NFKC/sem acento feita na aplicação). OpenSearch só por critério do ADR-014. Dado cifrado nunca entra na busca.

### Mapeamento de praias e setores (QGIS/KML, ADR-016)

- O mapeamento é **autorado no QGIS** (EPSG:4326) e pode ser entregue em **KML/KMZ**; o backend **não lê KML em produção**. Converter para **GeoJSON (RFC 7946)** com `ogr2ogr` no pipeline offline; o GeoJSON é o formato canônico de importação e do Region Pack.
- Camadas esperadas: `beaches` (polígono da praia) e **`beach_sectors`** (setor/trecho operacional dentro da praia), além de postos/bases e zonas de risco. Cada feição precisa de `external_id` estável, `name` e `source`.
- **Setor** é a unidade de operação: plantão, bandeira, ocorrência, sinalização e ocupação podem referenciar `beach_sector_id`. O setor herda a praia quando não informado; a soma dos setores não precisa cobrir 100 % da praia.
- Importação **idempotente por `external_id`** (upsert), versionada e auditada, com validação `ST_IsValid`/`ST_MakeValid`, reprojeção para 4326, descarte de Z e conferência de sobreposição. Guardar a geometria completa; simplificar só a cópia do cliente. Ver `docs/Architecture.md` §7.7 e `docs/adr/016-importacao-de-mapeamento-qgis-kml.md`.

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

> **Estado (2026-11-10):** a migração *feature-based* está concluída, as áreas por perfil já existem no cliente e o `/login` usa o **backend real** (`/api/v1/auth/*`), com redirecionamento por papel. O acesso demo (`VITE_DEMO_AUTH=true`) existe só para desenvolvimento sem backend. Se o código divergir deste arquivo, **confie no código** e corrija o arquivo.

- Vite 8 + React 19 + TypeScript 6 + Tailwind CSS 4 (tema em `index.css` via `@theme`, paleta `ocean`/`sand`/`coral`). PWA único; as áreas operacionais são *chunks* lazy.
- **Universal (qualquer CBM/estado):** nada regional no código. `.env`: `VITE_MAP_DEFAULT_CENTER` (JSON; padrão `[-51.9253, -14.2350]`, centro do Brasil), `VITE_MAP_DEFAULT_ZOOM`, `VITE_MAP_BOUNDS` (opcional; sem ele, navegação livre), `VITE_PMTILES_URL`, `VITE_API_BASE_URL` (**vazio = mesma origem**, para o cookie de refresh funcionar no proxy do Vite/Nginx), `VITE_DEMO_AUTH` (acesso demo em dev, exige `true` explícito). Dados de demonstração são contratos neutros prontos para KML/API.
- **Login e sessão no cliente:** `features/auth` concentra `LoginPage`, `session.ts` (Zustand **sem `persist`**, `status: unknown | anonymous | authenticated`), `session-boot.ts` (refresh no carregamento), `guards.tsx` (`RequireRole` + `next` só interno), `use-logout.ts`, `api.ts` (`refreshSingleFlight` com `navigator.locks`) e `demo.ts` (acesso demo só com `VITE_DEMO_AUTH=true` e `import.meta.env.DEV`). `/login` redireciona por papel via `homePathForRole`; a guarda de rota é UX e a autorização real é do servidor.
- **Home é um mapa de tela cheia** (RF01): `MapView` (chunk lazy; MapLibre v6.17.0) com estilo local de demonstração, marcadores DOM, fichas (sheets) e chips de camadas (Riscos, Pins, Grade), botão de centralizar (geolocalização sob demanda, não persistida), fallback sem WebGL e PMTiles por `VITE_PMTILES_URL` (ADR-005). Cabeçalho flutuante sobre o mapa na home.
- **Worker do MapLibre precisa de URL explícita:** `import maplibreWorkerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?url'` + `setWorkerUrl(...)` no escopo do módulo; `web/nginx.conf` tem `location ~* \.mjs$` com `default_type application/javascript`.
- **Marcadores (regras que evitam o "teleporte" e o sumiço):**
  - O MapLibre posiciona o elemento raiz do marcador com `transform: translate(...)` **inline**. **Nunca** aplique `transform`, `scale`, `transition: all` nem mude tamanho/margem **no elemento raiz** (nem em `:hover`). O raiz é só âncora; efeitos de hover/seleção vão num **filho** (`.mld-marker-inner`).
  - Todo marcador leva `data-map-marker="true"`; o clique usa `preventDefault()` + `stopPropagation()`; filhos visuais (label, dot, badge) levam `pointer-events-none`; o `map.on('click')` ignora alvos dentro de `[data-map-marker='true']` antes de fechar qualquer ficha.
  - Crie os marcadores **uma vez** e atualize o estado selecionado por classe; não remova/recrie todos a cada seleção (piscada, perde o hover).
  - Tocar em qualquer pin **sempre** abre uma ficha (RF38): posto fixo/base móvel (`StationSheet`) e sinalização com galeria (`SignalSheet`). **Praia não tem marcador**: é acessada pela lista/ficha (`BeachSheet`).
- **Acessibilidade/lint (obrigatório, RNF18):** `<output>` no lugar de `role="status"`; `<dialog open>` no lugar de `role="dialog"` (BeachSheet, SignalSheet, StationSheet, SosFab, AppMenu); props de componentes e contratos de tipos `readonly`; sem operador `void` nem parâmetros não usados (use `_nome`); `array.at(-1)`. Overlays usam `useOverlay` (foco em `[data-autofocus]`, Escape, Tab preso, retorno de foco).
- **SOS flutuante com confirmação** (ADR-001): FAB em todas as telas exceto `/sos`; o envio real é só em `/sos`, com resposta do servidor, timeout de 5 s, `clientRequestId` gerado uma vez e reutilizado, e "Ligar 193" sempre visível. Sem promessa de envio offline.
- **Aprender** (`LearnPage`): chips por categoria; 3 fases em acordeão (o que fazer · como identificar · prevenção); *facade* do YouTube sob demanda em `youtube-nocookie.com`.
- **Estado/armazenamento:** Zustand só para preferências não sensíveis (`selectedBeachId`, `installPromptDismissed`). **Nunca** persistir coordenadas, `clientRequestId`, CPF, e-mail, telefone **nem tokens** (`localStorage`/`sessionStorage`). Validação com Zod; o backend revalida. Sanitização de exibição em `sanitize.ts` (DOMPurify); proibido `dangerouslySetInnerHTML` com dado não confiável.
- **Cliente HTTP:** `shared/lib/api-client.ts` com timeout, erros RFC 9457 (`ApiProblem`) e *bridge* de autenticação (`AuthBridge`) que injeta o Bearer em memória e repete uma vez em 401 após `refreshSingleFlight` (`navigator.locks`); `features/auth/api-auth.ts` instala a *bridge*. **Nenhum token** é persistido.
- **PWA:** `manifest.webmanifest`, `sw.js` manual versionado (**nunca** cacheia `/api/**`, `/tiles/**`, WebSocket, não-GET, `Range` nem rotas autenticadas `/operador`, `/gestao`, `/admin`), `offline.html`, ícones por `npm run icons`.
- **Nginx:** CSP restritiva (sem inline/eval; `blob:` só em `worker-src`/`img-src`; `frame-src https://www.youtube-nocookie.com`), headers de segurança, proxy `/api/` para `backend:8080`.
- Dev: `npm run dev` na porta 3000 com proxy `/api` para `localhost:8080` (`VITE_PROXY_TARGET`).

### Estrutura de pastas (alvo: *feature-based*, ADR-015)

```
web/src/
├── app/        # App.tsx, main.tsx, index.css, routes.tsx, layout/ (RootLayout, AppMenu, OfflineBanner, InstallPrompt), pages/ (HomePage, About, NotFound)
├── shared/     # components/ (Card, Badges, DemoNotice, Dialog/Sheet), lib/ (api-client, sanitize, format, id, overlay, storage), types/
└── features/   # map, beaches, stations, signals, sos, tides, learn, search, auth, occurrences, operator, management, admin
    └── <feature>/{api,components,types,pages}/ + index.ts (API pública)
```

- **Regras:** feature importa só de `shared/` e de si; entre features só via `index.ts` e só `operator→occurrences`, `operator→stations`, `management→occurrences`, `admin→stations`, `*→auth`; `shared/` nunca importa de `features/` nem `app/`; composição entre domínios (ex.: home = map + beaches + stations + signals) fica em `app/`; tipos de domínio na feature; `map` não conhece praia (recebe camadas por props). As fronteiras são verificadas por `no-restricted-imports` em `web/eslint.config.js` (sem dependência nova).
- **Estado da migração:** **concluída em 2026-11-10** — o código está em `app/`, `shared/` e `features/`; não há mais `components/`, `pages/`, `lib/`, `types.ts` na raiz de `src/`. `admin-auth-store` e `AdminPage` provisórios foram removidos. A tabela de/para histórica está em `docs/Implementation.md`, Etapa 8A.
- **Rotas:** públicas `/`, `/praias`, `/praias/:beachId`, `/sos`, `/mares`, `/aprender`, `/sobre`, `*`; `/login`; áreas por papel `/operador/**` (SOS, ocorrências, postos), `/gestao/**` (equipe/painel, ocorrências) e `/admin/**` (visão geral, usuários, postos) — fora do `RootLayout` público com mapa.
- **Áreas operacionais:**
  - `/operador`: fila de SOS, registro de ocorrência (inclusive `FIELD_ATTENDED`) e cadastro de posto/base com localização atual. O formulário de posto/base é `features/stations/components/StationRegistry` (compartilhado com `/admin`, não duplicado).
  - `/gestao`: painel da equipe com plantão, última posição conhecida, contadores de ocorrências e sinalizações por operador; ativação/desativação de guarda-vidas; revisão de ocorrências/CIOSP.
  - `/admin`: apenas visão administrativa, cadastro/convite de usuários, papéis, postos/bases e auditoria. **Sem roadmap, status de integração ou planejamento de desenvolvimento.**
- **Mapa de arquivos:** `app/` (`App.tsx`, `main.tsx`, `index.css`, `routes.tsx`, `layout/` com `RootLayout`, `AreaShell`, `AppMenu`, `OfflineBanner`, `InstallPrompt`, `OperatorLayout`, `ManagementLayout`, `AdminLayout`; `pages/` com `HomePage`, `AboutPage`, `NotFoundPage`); `shared/` (`components/`, `lib/`, `types/`); `features/{map,beaches,stations,signals,sos,tides,learn,search,auth,occurrences,operator,management,admin}/`.
- **Imagens de Postos de Guarda-Vidas Fixos e Moveis** quando o usuário selecionar um posto de guarda-vidas devemos mostrar as imagens do posto para facilitar a identificação pela população.
- **Imagens dos Alertas e Sinalizações** o campo de detalhes da sinalização quando selecionada deve ter uma estrutura de galeria pois podemos ter mais de uma imagem.
- **Marcadores de Alertas e Postos de Guarda-Vidas** devemos padronizar os icones com uma descrição no canto inferior esquerdo da pagina sob o aviso do 193. Devemos usar marcadores de localização para alertas onde a cor dele será atrelada a bandeira. Para os marcadores de postos de guarda-vidas devemos padronizar algo que lembre guarda-vidas tendo um icone para fixos e outro icone para movel que facilite a identificação. O objetivo e que a população possa identificar o que é sem precisar olhar nem ler nada.

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

- Antes de criar tela, hook ou componente novo, decidir a feature dona; se for usado por mais de uma, subir para `shared/` — não importar de outra feature fora da regra.
- Dado de vítima, localização precisa e resumo de ocorrência **nunca** aparecem em log, outbox, evento, cache, métrica nem em resposta de listagem sem máscara.
- Mudança de arquitetura ou de requisito atualiza `docs/` e `AGENTS.md` **no mesmo PR**; decisão relevante vira ADR.

## Diretrizes de banco, cache e idempotência

- **Views** para projeções de leitura recorrentes; **views materializadas** para agregações/snapshots caros, com índice único + `REFRESH ... CONCURRENTLY` e estratégia de atualização explícita. Não usar view materializada onde o dado precisa ser fresco (SOS, bandeira vigente, postos disponíveis).
- **`FOR UPDATE SKIP LOCKED`** para filas/consumo concorrente (Outbox, escalonamento de SOS, notificações), com transações curtas, índice parcial de status e idempotência.
- **Cache Redis** para leituras quentes (GeoJSON, contexto, bandeira) em cache-aside, chaves versionadas, TTL e invalidação por evento; proteger contra stampede e ter fallback se o Redis cair. Cache nunca é fonte da verdade para SOS/ack/escrita.
- **Idempotência** em escritas com retry/duplo toque: `client_request_id` único, `Idempotency-Key`, constraints únicas/`ON CONFLICT`, consumidores idempotentes e deduplicação no Outbox.
- **Geoindexação:** distância **sempre** em metros via `geography` (`ST_DWithin`/`ST_Distance`); `geometry` 4326 só para polígonos e `ST_Contains`. Índice **GiST** existe para consulta que filtra por área — não para ponto lido por chave. No SOS: GiST em `lifeguard_posts`/`mobile_bases.location` e em `emergency_calls.location` (coluna gerada de `latitude`/`longitude`), `ST_DWithin` + KNN `<->` para escolher postos, `ST_Contains` para geofence (que **não** bloqueia o chamado) e B-tree parcial para a fila de escalonamento. Detalhes em `docs/Architecture.md` §7.12.
- Medir com `EXPLAIN (ANALYZE, BUFFERS)` antes de adicionar índices, views ou materializações; documentar a consulta e o ganho.
- Detalhes em `docs/Architecture.md` §7.6.

## Roadmap conhecido (próximas entregas plausíveis)

- `identity`: TOTP para ADMIN, cadastro/convite de usuários e gestão de papéis pela TI, escopo por praia/setor para `COORDINATOR` e trilha de auditoria de acesso
- `emergency` (SOS): concluir walking skeleton com publicação WebSocket/Web Push, ack com compare-and-set, escalonamento e validação de latência/resiliência; inclui a **geoindexação** (coluna gerada + GiST em `emergency_calls`, consulta de postos elegíveis com `ST_DWithin`/KNN — `docs/Architecture.md` §7.12); o frontend já envia o SOS real
- `beaches`: repositórios/CRUD, validação `ST_IsValid`, fichas e GeoJSON público — substitui os dados de demonstração do PWA
- `beaches`: CRUD de zonas `PERMANENT`/`SEMI_PERMANENT`, revalidação de zonas vencidas e filtro temporal no GeoJSON — ver `docs/Architecture.md` §7.8
- `operations`: `rip_current_markings` para correntes efêmeras/migratórias (RF13), com `valid_until` curto e evento `RipCurrentMarked`
- `beaches`: importador de delimitações via QGIS/KML/GeoJSON, incluindo **`beach_sectors`** (reprojetar para 4326, validar e fazer upsert idempotente por `external_id`) — ver ADR-016 e `docs/Architecture.md` §7.7
- `region`: `installation_settings` e Region Pack
- `tides`/`content`: endpoints públicos de marés e vídeos; hoje o PWA usa dados de demonstração
- `occurrences`: migração `V8__occurrences.sql` (ou próxima livre), criação por evento do SOS + reconciliação, registro externo (193), fila e ficha CIOSP (Etapa 6A; ADR-013)
- `search`: `pg_trgm` + `search_documents` + `SearchPort` e conjunto de teste de grafias erradas (Etapa 14A; ADR-014)
- Frontend: corrigir o hover dos marcadores e a ficha dos pins; cadastro de posto/base com localização atual (compartilhado entre operador e TI); painel da gestão com posição/contadores; formulário de ocorrência do operador (`FIELD_ATTENDED`); **remover qualquer roadmap/status de integração da área `/admin`**
- Frontend: tiles PMTiles gerados + camadas com dados reais (postos, bandeira vigente, alertas)
- `beaches`/`region`: importador QGIS/KML → GeoJSON com `beach_sectors` e Region Pack (ADR-016)
- Migração para Spring Boot 4.1.x + linha compatível do Spring Modulith (ADR-011)
- Pipeline CI: base pronta (build + testes com PostGIS e Redis); evoluir com build/lint do frontend, Testcontainers Garage, SAST/SCA e cobertura

Validação local mais recente (2026-11-10): `cd web && npm run build && npm run lint` após as telas de operador/gestão/TI. Backend com `./gradlew clean check` verde (64 testes) e login real das três contas validado contra o Postgres local (`V6`/`V7` aplicadas, seed `IDENTITY_DEV_SEED=true`). Fluxo de login no navegador validado com Playwright/Chromium (19 checagens): redirecionamento por papel, reload com refresh, `next` externo ignorado, logout e rota protegida sem sessão; a API respondeu 401 sem token, 403 para papel errado em `application/problem+json` e 404 para ADMIN em rota administrativa ainda não implementada.

---
Última atualização: 2026-11-10, após as decisões de operação de campo (ocorrências atendidas e postos pelo operador), painel da coordenação, área da TI sem roadmap, importação QGIS/KML com setores (ADR-016) e documentação da geoindexação do SOS (`docs/Architecture.md` §7.12).