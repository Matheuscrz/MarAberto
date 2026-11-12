# AGENTS.md — Memória de contexto do projeto

Memória de contexto para agentes de IA neste repositório. Se o código divergir deste
arquivo, **confie no código** e corrija o arquivo. Mudança de arquitetura ou de requisito
atualiza `docs/` e este arquivo **no mesmo PR**; decisão relevante vira ADR.

## Descrição

**MarAberto** (repositório `Praias-Seguras`) — plataforma open source de segurança
balnear: mapa de riscos, bandeiramento, tábua de marés, reportes comunitários com
quórum, estimativa anônima de ocupação e **SOS cidadão** para guarda-vidas.
Ferramenta de **apoio**; não substitui o acionamento oficial (ligar 193).

## Direção atual (2026-11-11)

- **Área de banho é a unidade territorial**: praia, rio e barragem, com **setores**
  operacionais dentro dela (ADR-016 + ADR-021). Nada regional no código.
- **Operação de campo**: plantão, ocorrências `FIELD_ATTENDED` e cadastro de postos/bases
  com a localização atual do aparelho (a posição não vira rastreamento contínuo).
- **Coordenação** (`/gestao`): importação do mapa de áreas de banho (primeira aba do perfil), plantão/última posição, contadores, sinalizações e CIOSP.
  **TI** (`/admin`): só usuários, papéis, postos/bases e auditoria — **sem roadmap e sem importação de mapa**.
- **SOS (ADR-018)**: o PostGIS decide os destinatários na transação do chamado; a outbox
  cifrada + STOMP autenticado entrega; ack por CAS (um chamado, um atendente);
  escalonamento por anéis de 5 a 20 km (`V10`).
- **Sessão**: `/login` único e três áreas por papel; refresh em cookie
  `__Secure-maraberto_rt; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Strict` e access
  token **só em memória**.
- **Estado**: backend com **120 testes** (`./gradlew clean check`); frontend com
  `npm run build` e `npm run lint` verdes; app Android (Capacitor 8) com APK debug gerado.

## Ler antes de trabalhar

- `docs/Architecture.md` — §7.5/§7.6 (SOS e escala), §7.7 (importação), §7.8 (zonas
  temporais), §7.12/§7.13 (geoindexação e despacho).
- `docs/Requirements.md` — RF/RN/RNF; **RF41** é a importação de mapeamento.
- `docs/adr/` — ADRs 001–021. Mais usadas: 012 (dados sensíveis), 013 (ocorrências),
  016 (importação), 017/018 (SOS), 019 (Capacitor), 020 (escala), 021 (área de banho).
- `docs/kml/Areas_Banho_Sergipe.kml` — mapeamento real de SE (fonte da Fatia 1); não
  editar à mão, reexportar do QGIS.

## Módulos (Spring Modulith)

- Pacotes em `com.maraberto` com `api/ application/ domain/ infra/` por módulo:
  `identity`, `region`, `beaches`, `operations`, `emergency`, `safety`, `tides`,
  `content`, `occupancy`, `media`, `realtime`, `audit`, `analytics`, `shared`.
  `ApplicationModules.verify()` (`ModularityTests`) protege as fronteiras.
- Com fluxo implementado: `beaches` (entidades, importação KML e setores), `emergency`
  (SOS completo até o ack), `identity` (login/RBAC/sessões) e `operations` (plantão e
  elegibilidade do SOS). `media` e `realtime` têm portas; o resto é `package-info`.
- **Nunca** importar de outro módulo fora da `api/` pública. `beaches` não importa
  `emergency`/`operations`; o SOS nunca espera a ocorrência.

## Backend

- Spring Boot 3.3.4 + Spring Modulith 1.2.13 (ADR-011), Java 21, Gradle, virtual threads,
  Swagger e `/actuator/health`. Migrar para Boot 4.1.x é issue futura.
- PostgreSQL 16 + PostGIS, Flyway e `ddl-auto: validate`; H2 removido.
- Migrações em `backend/src/main/resources/db/migration/`: `V1` PostGIS e tabelas
  espaciais · `V2` zonas temporais · `V3` SOS · `V4` proteção de dados · `V5` outbox ·
  `V6`/`V7` identidade e sessões · `V8` despacho + plantão · `V9` lease do plantão ·
  `V10` escalonamento em anéis · `V11` áreas de banho/setores/lotes de importação.
  **Próxima livre: `V12`** (ocorrências).
- `application.yml` único. Chaves relevantes: `maraberto.dispatch.*` (raio padrão, teto
  `max-radius-m: 15000`, `position-stale-after: PT30M`, `shift-idle-timeout: PT15M`,
  `escalation.rings: 5000,7000,10000,12000,15000,20000` e `escalation.delay: PT20S`),
  `maraberto.emergency.outbox.*`, `maraberto.security.crypto.*`, `maraberto.security.jwt.*`,
  seeds `maraberto.identity.dev-seed.enabled`, `maraberto.operations.dev-seed.enabled` e
  `maraberto.beaches.dev-seed.enabled` (este exige a tabela `beaches` vazia e recusa o
  perfil `prod`; `maraberto.beaches.import.file` atende a importação assistida).
  `@Scheduled` aceita **somente ISO-8601**.
- Redis é opcional no boot e **nunca** é fonte da verdade. Erros HTTP em RFC 9457
  (`application/problem+json`, com `type`, `title`, `status`, `detail`, `instance`) e
  todo endpoint novo documentado no OpenAPI com `@Schema` quando for contrato.
- **Lease do plantão:** `shift_sessions.last_seen_at` renovado pelo console a cada 5 min;
  sem sinal por `PT15M` o plantão deixa de ser elegível **na consulta**, e o job só
  carimba `IDLE_TIMEOUT`. Logout encerra o plantão.
- **Distância do SOS é medida pela posição do plantão** (`COALESCE(last_position,
  estrutura)`), nunca pela estrutura quando há posição — o bug real foi 207 km. Check-in
  não abre plantão sem posição.
- Testes usam `PostgisIntegrationTest` (PostGIS compartilhado pela JVM; limpa
  outbox/despachos/chamados/plantões antes de cada teste) e deixam os sweeps em `PT1H`
  em `src/test/resources/application.properties`.

## Segurança de dados e convenções

- Decisão completa na ADR-012. **Todo DTO de request com texto usa `@Sanitized`**
  (`PLAIN_TEXT`, `RICH_TEXT`, `IDENTIFIER`, `EMAIL`, `PHONE`, `CPF`, `URL`, `YOUTUBE_ID`).
- Dado sensível novo usa `EncryptedStringConverter`/`SensitiveDataProtector` (AES-256-GCM
  e índice cego HMAC); **nunca** PII em claro em log, outbox, fila, cache, métrica ou
  storage web. Campos cifrados não aceitam `LIKE`, ordenação ou range.
- **Proibido** token em `localStorage`/`sessionStorage`; coordenada, `clientRequestId`,
  CPF, e-mail e telefone também não são persistidos no cliente.
- Commits em **pt-BR** com Conventional Commits; nomes de classes em inglês; sem
  comentário óbvio. **Não commitar sem aval explícito.** `docs/kml/` e
  `web/src/assets/*.jpg` são do usuário.
- Banco: `FOR UPDATE SKIP LOCKED` para filas, transações curtas, índice parcial e
  idempotência por constraint (`client_request_id`/`external_id`). Distância sempre em
  metros via `geography`; `geometry` 4326 para polígonos e `ST_Contains`. Medir com
  `EXPLAIN (ANALYZE, BUFFERS)` antes de criar índice; detalhes em `§7.6`.

## Frontend (`web/`)

- Vite 8 + React 19 + TypeScript 6 + Tailwind 4; **feature-based** (ADR-015):
  `app/`, `shared/`, `features/`. Feature importa só de `shared/` e de si; entre features
  só pela `index.ts` e apenas `operator→occurrences`, `operator→stations`,
  `management→occurrences`, `admin→stations`, `*→auth`. Fronteiras no ESLint.
- Rotas públicas: `/`, `/praias`, `/praias/:beachId`, `/sos`, `/mares`, `/aprender`,
  `/sobre`; `/login`; áreas `/operador/**`, `/gestao/**` (com `/gestao/importar` como
  primeira aba), `/admin/**`.
- Home é mapa de tela cheia (MapLibre; worker via `?url` + `setWorkerUrl`). **Marcador**:
  o elemento raiz recebe `transform` inline do MapLibre — nunca aplicar `transform`,
  `scale`, `transition` nem mudar tamanho nele; efeitos vão num filho. Marcadores são
  criados uma vez e atualizados por classe; todo pin tem `data-map-marker="true"` e
  sempre abre uma ficha (praia não tem marcador, é acessada pela lista).
- Acessibilidade obrigatória: `<output>` em vez de `role="status"`, `<dialog open>` em
  vez de `role="dialog"`, overlays por `useOverlay` (foco, Escape, Tab preso).
- PWA: service worker manual **nunca** cacheia `/api/**`, `/tiles/**`, WebSocket, não-GET
  nem rotas autenticadas. Nginx e Vite fazem proxy de `/api` e `/ws` (Upgrade).
- **App mobile (ADR-019)**: mesmo `web/` em WebView (Capacitor 8), sem fork de UI;
  `shared/lib/platform.ts` e `shared/lib/native.ts` são os únicos que importam
  `@capacitor/*`; no nativo o `api-client` usa `CapacitorHttp` e o cookie é tratado pelo
  `CookieHandler` do processo. O backend **não muda** por causa do app (sem CORS,
  `SameSite=Strict` intacto); `REALTIME_ALLOWED_ORIGINS` precisa listar
  `https://localhost`/`capacitor://localhost`. `mobile/` (Flutter) está congelado.

## Fatia 1 — importador de áreas de banho (KML → PostGIS)

**Status:** implementada no working tree (backend + testes + aba da gestão); falta o aval
para commitar. Entregue: `KmlFeatureReader`, `BathingAreaImportService`,
`BathingAreaRepository`, `V11`, endpoint da coordenação, seed de dev e
`ImportMapPage` em `/gestao`. O fluxo é **síncrono** nesta fatia; a fila (Rabbit ou
tabela de jobs) entra quando houver volume/concorrência medidos.

Objetivo: ler `docs/kml/Areas_Banho_Sergipe.kml`, converter as geometrias para
`geometry(...,4326)` com JTS e persistir **idempotentemente** em `beaches`,
`beach_sectors` e proveniência (ADR-016). O leitor de KML é **ferramenta de importação**
(seed de dev + endpoint da gestão opt-in), **não** parser do caminho de produção: o GeoJSON
continua canônico no Region Pack (emenda da ADR-016).

### O que o arquivo tem (levantado em 2026-11-11)

- **45 `<Placemark>` = 45 setores**, todos `MultiGeometry > Polygon` (1 a 3 polígonos por
  feição). **32 praias distintas** por (`Municipio`, `Praia`).
- `ExtendedData`: `id` (1–45, único), `Praia`, `Setor`, `Municipio`, `Estado` (SE) e
  `Num. de Oc` (contagem histórica — guardar como metadado de origem, **não** como
  contador vivo).
- **`Estado` pode faltar**: o CBM informa a UF no envio, então o importador recebe `state`
  como parâmetro (seed/endpoint). Quando a feição traz `Estado`, ele precisa bater com o
  parâmetro; quando não traz, vale o parâmetro. Sem UF (nem no arquivo nem no envio) a
  importação é rejeitada — `beaches.state_code` é `NOT NULL`.
- **Nome não identifica nada**: "Prainha da Orla" existe em Canindé, Propriá e Neópolis;
  no Brasil inteiro há homônimos de município e de praia. A chave da área é
  (`state_code`, `municipio`, `praia normalizada`) e **nome de setor nunca é chave**.
  Dois setores da mesma praia podem ter o mesmo nome; o que os distingue é o `id` da
  origem (o `external_id`) e a `display_order`.
- **Setor sem nome**: `Setor = "-"` em **20 das 45** feições (ex.: Praia do Restinho,
  Praia da Costa, Barragem de Lagarto). Não é "praia inteira": é o **único setor sem
  nome** daquela praia. Guardar `name` nulo + `display_order`; a UI mostra "Setor N".
- O arquivo **mistura praia oceânica, banho de rio e barragem** (S. do Socorro, Propriá,
  Neópolis, Santana do São Francisco, Pacatuba, Porto da Folha e as três "Barragem de…").
- Não há postos, bases nem zonas de risco no arquivo — essas camadas virão em outra fatia.

### Modelo e migração `V11`

- `beaches` mantém o nome da tabela (FKs de postos/zonas já existem), mas passa a
  significar **área de banho**: adicionar `kind` (`BEACH`/`RIVER`/`DAM`/`LAGOON`/`OTHER`,
  default `BEACH`), `external_id`, `source`, `source_version`, `imported_at`, `imported_by`,
  `source_metadata JSONB`, `active` e trocar `boundary` para `geometry(MultiPolygon,4326)`.
- `beach_sectors`: tabela nova com `beach_id`, `external_id` (= `<uf>-areas-banho:<id>`),
  **`name` nulo**, `display_order`, `active`, `geometry_hash`, geometria `MultiPolygon` e a
  mesma proveniência; `UNIQUE (external_id)`, `CHECK (ST_IsValid(geometry))`, GiST na
  geometria.
- `import_batches`: `source_file`, `source_version` (SHA-256), `imported_at`, `imported_by`,
  `accepted`, `rejected`, `unchanged`, `missing` e `report JSONB` — auditoria e `dry-run`.
- `beaches.boundary` = união dos setores daquela (`state_code`, `municipio`, `praia`); cada
  placemark vira **um** setor. Feição ausente na reimportação **não** é apagada: só
  relatada.
- **UUID estável**: o upsert é por `external_id` e **preserva o `id`** da área/setor, para
  que `lifeguard_posts`, `critical_zones`, `shift_sessions` e `occurrences` nunca apontem
  para uma linha recriada. **A âncora da área é o menor `id` de setor do grupo**
  (`<uf>-areas-banho:area:<min-id>`), não o nome: renomear a praia no QGIS atualiza a mesma
  área em vez de criar outra. Se essa âncora sumir do arquivo, o importador casa pelo
  (`state_code`, `municipio`, `praia` normalizada) e mantém o UUID; se nem isso casar,
  reporta "área ausente + área nova" e a fusão é decisão humana.
- **`external_id` inclui a UF** (`<uf>-areas-banho:<id>`): o `id` do KML é único no arquivo,
  não no país. Sem o prefixo da UF, um KML de outro estado com o mesmo `id` colidiria na
  constraint `UNIQUE` e sobrescreveria a praia errada.
- **Nome de setor é rótulo, não identidade.** `beach_sectors.name` aceita nome repetido
  (inclusive dentro da mesma praia) e nome nulo; a identidade é o `external_id` e a ordem é
  a `display_order`. O `UNIQUE` é só em `external_id` — nunca em (`beach_id`, `name`).
- `kind`: o KML não traz o tipo. Ordem de decisão: campo `Tipo` no `ExtendedData` quando
  o QGIS passar a exportá-lo → nome iniciado por `Barragem`/`Açude` = `DAM` → default
  `BEACH`. A classificação de rio é ajustável pelo cadastro/endpoint, sem reimportar.

### Importar e **atualizar** (reimportação)

O mesmo endpoint e o mesmo *seed* servem para a carga inicial e para a atualização. O CBM
não precisa mandar um arquivo "limpo": manda o mesmo KML reexportado com as mudanças, e o
importador descobre o que é novo, o que mudou e o que sumiu.

- **Entrada:** arquivo KML (multipart) + `state` (UF informada pelo CBM) ou caminho
  configurado (`maraberto.beaches.import.file`). KMZ fica para depois (exige descompactar);
  o QGIS exporta KML ou GeoJSON. A UF entra no prefixo do `external_id`
  (`<uf>-areas-banho:`), então o mesmo `id` em estados diferentes não colide.
- **Sempre em duas fases:** `dryRun=true` (padrão) devolve o relatório sem escrever;
  a aplicação exige `dryRun=false` **e** `confirm=true`.
- **Modos:** `UPSERT` (padrão) cria e atualiza, **nunca** remove; `SYNC` (opt-in) marca
  como `active=false` as feições que sumiram do arquivo — **soft delete**, sem apagar
  linha nem quebrar FK. Reativar uma feição que voltou é automático.
- **Cenários cobertos:** praia nova → `beaches` + setores `created`; setor novo em praia
  existente → setor `created` e `beaches.boundary` recalculado só para aquela área;
  polígono/nome/ordem alterados → `updated`; setor removido → `missing` (e `active=false`
  em `SYNC`, com o `boundary` recalculado a partir dos setores ativos); praia renomeada →
  `updated` (a âncora pelo menor `id` de setor mantém o UUID); **mesmo nome em outro
  município/estado** → duas áreas distintas, nunca um merge por nome.
- **Diff por feição:** compara `external_id` + `geometry_hash` (WKB normalizado + SHA-256)
  + campos (`Praia`, `Setor`, `Municipio`, `kind`, `display_order`). O relatório classifica
  em `created`/`updated`/`unchanged`/`rejected`/`missing`, com os campos alterados; só as
  linhas realmente diferentes são escritas.
- **Arquivo idêntico é barato:** se o SHA-256 do arquivo for igual ao do último lote
  aplicado com sucesso, o relatório responde "sem mudanças" sem reescrever nada (a
  validação do parser continua rodando).
- **Uma transação por lote**, `ON CONFLICT (external_id) DO UPDATE`, e `source_version`
  (hash do arquivo) gravado no lote e em cada feição. Falha no meio não deixa mapa
  parcialmente atualizado.
- **Mapa do banhista:** ao aplicar, publicar `BeachChanged`/`SectorChanged` para invalidar
  o GeoJSON público em cache; o frontend recarrega as camadas. Nada de TTL curto como
  mecanismo de atualização.

### Passos

1. `KmlFeatureReader` (XML namespace-aware, **XXE/DTD desligados**) → `ImportedFeature`
   (`id`, `praia`, `setor`, `municipio`, `uf`, `MultiPolygon` JTS).
2. `BathingAreaImportService`: recebe `state`, agrupa por (`state_code`, `municipio`,
   `praia` normalizada) com âncora no menor `id` de setor, valida (`ST_IsValid`/
   `ST_MakeValid`), força 2D, reprojeta se preciso, rejeita vazio/auto-intersectado,
   recalcula `beaches.boundary` apenas nas áreas afetadas e devolve relatório de
   aceitos/rejeitados/alterados/ausentes.
3. `JdbcBathingAreaRepository` com `ON CONFLICT` por `external_id`, em transação por lote;
   `dry-run` é o padrão.
4. Exposição: `maraberto.beaches.dev-seed.enabled` (importa se a tabela estiver vazia) e
   `POST /api/v1/management/bathing-areas/import?state=&dryRun=&confirm=&mode=`
   (`COORDINATOR`; `ADMIN` só como superusuário técnico, sem entrada na área da TI;
   `dryRun=true` por padrão, relatório de diff em JSON e erros em
   `application/problem+json`). O mesmo endpoint aceita o arquivo novo do QGIS e atualiza
   as geometrias existentes.
5. Testes: parser isolado com fixture pequena, integração PostGIS (importar **duas
   vezes**, contar linhas, validar SRID/geometria/`ST_IsValid`, setor sem nome, barragem
   reconhecida), mudança de geometria refletida em `updated`, praia nova em `created`,
   praia renomeada mantendo o UUID, feição removida vira `missing` sem apagar, `state`
   como fallback quando o arquivo não traz `Estado`, e `ModularityTests`.
6. Frontend — **gestão primeiro**: `ImportMapPage` em `/gestao/importar`, **primeira aba e
   tela inicial do perfil** (`COORDINATOR` entra na gestão já na importação). A página
   envia o KML com `state`, mostra `dry-run`/relatório (`created`/`updated`/`unchanged`/
   `missing`/`rejected`), exige confirmação para aplicar e deixa claro que o arquivo
   substitui/atualiza o mapa. A TI (`/admin`) continua só com usuários, papéis, postos e
   auditoria. `/praias` e a ficha leem as áreas de banho e listam setores (nome ou
   "Setor N"); a camada vem do GeoJSON público, não do KML.
7. Segurança: o endpoint fica em `/api/v1/management/**` (`COORDINATOR`, `ADMIN` como
   superusuário técnico); upload limitado por tamanho/tipo, conteúdo processado fora do
   request do banhista e o relatório nunca expõe coordenada exata de operador.

### Critérios de aceite

- KML lido sem erro; **45 setores** e **32 praias** por (`municipio`, `praia`).
- Reimportar o mesmo arquivo: `unchanged=45`, `created=0`, `updated=0`, `missing=0`.
- Reimportar depois de mover um polígono no QGIS: aquele setor aparece em `updated`, os
  demais em `unchanged`, e o `id`/UUID da área e do setor **não muda**.
- Arquivo novo com uma praia a mais: a praia e seus setores aparecem em `created`; o
  `boundary` da área é recalculado e as demais áreas ficam `unchanged`.
- Praia renomeada no QGIS: `updated` na mesma área (UUID preservado), não `created` +
  `missing`.
- `SYNC` com uma feição removida marca `active=false` e não apaga a linha; reimportar
  com ela de volta reativa.
- Arquivo sem `Estado`: a UF vem do parâmetro `state`; arquivo com `Estado` divergente do
  parâmetro é rejeitado com erro claro.
- Duas praias de mesmo nome em municípios diferentes (e duas em estados diferentes) viram
  **quatro** áreas; dois setores de mesmo nome na mesma praia viram **dois** setores, sem
  colisão de `UNIQUE`.
- Geometrias válidas em 4326 (`ST_IsValid`), sem Z e sem coordenada fora da UF.
- A coordenação entra em `/gestao` na aba **Importar mapa**, envia o KML com a UF, vê o
  relatório em `dry-run` e só aplica após confirmar; a TI não tem essa aba.
- `ModularityTests` verde e `beaches` sem importar `emergency`/`operations`.

## Roadmap conhecido (curto)

- `beaches` — Fatia 1 acima; depois CRUD/GeoJSON público, zonas `PERMANENT`/
  `SEMI_PERMANENT` com filtro temporal e importação de postos/bases/zonas.
- `occurrences` — `V12` (depois da `V11`), criação por evento do SOS + reconciliação,
  193/`FIELD_ATTENDED` e ficha CIOSP (ADR-013).
- `operations` — `rip_current_markings` para correntes efêmeras (RF13).
- `region` — `installation_settings` e Region Pack.
- `tides`/`content`/`search` — endpoints públicos de marés/vídeos e `pg_trgm` (ADR-014).
- `emergency` — transição `RESOLVED`/`FALSE_ALARM`, perfil `rabbit` com DLQ, Web Push
  com o app fechado, métricas/k6 (gatilhos da ADR-020).
- Frontend — cadastro persistido de postos/bases, painel da gestão com posição,
  formulário de ocorrência, tiles PMTiles e ícones padronizados.
- App mobile — câmera/push, ícone/splash próprios, keystore e teste em aparelho físico.
- CI — lint/build do frontend, Testcontainers Garage, SAST/SCA e cobertura.
