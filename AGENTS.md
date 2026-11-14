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
- **Estado**: backend com **188 testes** (`./gradlew clean check`); frontend com
  `npm run build` e `npm run lint` verdes; app Android (Capacitor 8) com APK debug gerado.
- **Sinalização e estruturas (ADR-022)**: `flag_updates` append-only com validade (12 h,
  teto 24 h) e `closed_at`; bandeira vencida sai do mapa e a ficha mostra a última cor com
  selo "desatualizada". Posto/base tem `active`/`deactivated_at`/`status_note`; base móvel
  só aparece com plantão vivo. Nada é apagado por tempo: a vigência é decidida na consulta.
- **Campo ancorado no aparelho (ADR-023/ADR-024)**: a bandeira pode ser lançada por
  praia/setor **ou** pela posição atual (o PostGIS resolve praia/setor) e, **fora do
  mapeamento, não é recusada** — vira registro de área não identificada (`V18`) para a
  coordenação triar, sem pin público. Até 3 fotos re-codificadas no servidor (EXIF/GPS
  fora). Posto/base é cadastrado e reposicionado pelo guarda-vidas com a posição atual
  (`V16`), recebe foto pública (`V19`) e a estrutura do **próprio plantão** pode ser
  ativada/desativada em campo.
- **Ocorrências (ADR-013 emendada)**: o **aceite do SOS** abre a ficha (`V17`) —
  `origin=SOS_CITIZEN`, `IN_PROGRESS`, `ciosp_status=PENDING`; chamado sem ack não gera
  ocorrência. Criação por evento depois do commit, idempotente por `emergency_call_id`, com
  job de reconciliação.

## Ler antes de trabalhar

- `docs/Architecture.md` — §7.5/§7.6 (SOS e escala), §7.7 (importação, arquivamento, lista
  pública, "onde estou" e geometria com `ETag`), §7.8 (zonas temporais), §7.9 (ocorrências),
  §7.11 (plantão, bandeira, estruturas e sinalização por localização), §7.12/§7.13
  (geoindexação e despacho), §8.2 (bandeiramento), §8.5 (ocupação), §8.6 (upload de foto),
  §8.7 (ocorrências) e §9 (cache).
- `docs/Requirements.md` — RF/RN/RNF; **RF41** é a importação de mapeamento.
- `docs/adr/` — ADRs 001–024. Mais usadas: 012 (dados sensíveis), 013 (ocorrências, gatilho
  no ack), 016 (importação), 017/018 (SOS), 019 (Capacitor), 020 (escala), 021 (área de
  banho), 022 (ciclo de vida da sinalização e das estruturas), 023 (sinalização por
  localização, mídia e estruturas de campo) e 024 (fila fora do mapeamento, estrutura em
  campo e foto do posto).
- `docs/kml/Areas_Banho_Sergipe.kml` — mapeamento real de SE (fonte da Fatia 1); não
  editar à mão, reexportar do QGIS.

## Módulos (Spring Modulith)

- Pacotes em `com.maraberto` com `api/ application/ domain/ infra/` por módulo:
  `identity`, `region`, `beaches`, `operations`, `emergency`, `occurrences`, `safety`,
  `tides`, `content`, `occupancy`, `media`, `realtime`, `audit`, `analytics`, `shared`.
  `ApplicationModules.verify()` (`ModularityTests`) protege as fronteiras.
- Com fluxo implementado: `beaches` (entidades, importação KML e setores, arquivamento do
  arquivo aplicado, API pública por proximidade e zonas de risco públicas), `emergency`
  (SOS completo até o ack, publicando `SosAcknowledged` em `api/`), `identity`
  (login/RBAC/sessões), `operations` (plantão, elegibilidade do SOS, sinalização por
  escopo/localização, fila de áreas não identificadas, cadastro/reposição/ativação de
  estrutura, leitura pública/admin), `occupancy` (indicador anônimo por setor), `media`
  (upload normalizado com `ImageIO`, leitura pública com `ETag`, `MediaCatalog` para os
  outros módulos) e `occurrences` (ficha aberta no ack, leitura por escopo,
  reconciliação). `realtime` tem porta; o resto é `package-info`.
- **Nunca** importar de outro módulo fora da `api/` pública. `beaches` não importa
  `emergency`/`operations`; o SOS nunca espera a ocorrência; `operations` pode ler
  `media.api` (`MediaCatalog`) e `media` não importa `operations`.

## Backend

- Spring Boot 3.3.4 + Spring Modulith 1.2.13 (ADR-011), Java 21, Gradle, virtual threads,
  Swagger e `/actuator/health`. Migrar para Boot 4.1.x é issue futura.
- PostgreSQL 16 + PostGIS, Flyway e `ddl-auto: validate`; H2 removido.
- Migrações em `backend/src/main/resources/db/migration/`: `V1` PostGIS e tabelas
  espaciais · `V2` zonas temporais · `V3` SOS · `V4` proteção de dados · `V5` outbox ·
  `V6`/`V7` identidade e sessões · `V8` despacho + plantão · `V9` lease do plantão ·
  `V10` escalonamento em anéis · `V11` áreas de banho/setores/lotes de importação ·
  `V12` chave do arquivo arquivado do lote · `V13` índice geográfico da projeção do
  mapa · `V14` amostras anônimas de ocupação · `V15` ciclo de vida da sinalização e das
  estruturas (`flag_updates`, `deactivated_at`/`status_note`) · `V16` posição da sinalização
  e mídia (`signal_position`/`position_source`, `media_assets`, `flag_media`, origem da
  posição das estruturas) · `V17` ocorrências (`occurrences` + `occurrence_events`) ·
  `V18` fila de áreas não identificadas (`unmapped_signals` + `unmapped_signal_media`) ·
  `V19` fotos das estruturas (`lifeguard_post_media` + `mobile_base_media`).
  **Próxima livre: `V20`**.
- `application.yml` único. Chaves relevantes: `maraberto.dispatch.*` (raio padrão, teto
  `max-radius-m: 15000`, `position-stale-after: PT30M`, `shift-idle-timeout: PT15M`,
  `escalation.rings: 5000,7000,10000,12000,15000,20000` e `escalation.delay: PT20S`),
  `maraberto.emergency.outbox.*`, `maraberto.security.crypto.*`, `maraberto.security.jwt.*`,
  `maraberto.storage.*` (provider `stub`/`s3`, endpoint/região/credenciais e `path-style`
  do Garage), `maraberto.occupancy.*` (janela `PT15M`, `minimum-sample: 5`, limiares
  `moderate/high/extreme`, retenção `PT24H`), `maraberto.flags.validity: PT12H` (validade
  padrão da bandeira; o lançamento aceita 0,5 h a 24 h),
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
  `operator→beaches`, `operator→signals`, `beaches→signals`,
  `management→occurrences`, `management→beaches`, `management→signals`,
  `admin→stations`, `*→auth`. Fronteiras no ESLint.
- Rotas públicas: `/`, `/praias`, `/praias/:beachId`, `/sos`, `/mares`, `/aprender`,
  `/sobre`, `/guarda-vidas`; `/login`; áreas `/operador/**` (`/operador/sinalizacao`,
  `/operador/postos`), `/gestao/**` (com `/gestao/importar` como primeira aba e
  `/gestao/areas-nao-identificadas` na triagem) e `/admin/**`.
- Home é mapa de tela cheia (MapLibre; worker via `?url` + `setWorkerUrl`). **Marcador**:
  o elemento raiz recebe `transform` inline do MapLibre — nunca aplicar `transform`,
  `scale`, `transition` nem mudar tamanho nele; efeitos vão num filho. Marcadores são
  criados uma vez e atualizados por classe; todo pin tem `data-map-marker="true"` e
  sempre abre uma ficha (praia não tem marcador, é acessada pela lista).
- Acessibilidade obrigatória: `<output>` em vez de `role="status"`, `<dialog open>` em
  vez de `role="dialog"`, overlays por `useOverlay` (foco, Escape, Tab preso).
- **Polling e cache do mapa:** postos/bases e sinalização consultam a cada **30 s**;
  zonas de risco e ficha da praia a cada **60 s**. Aba escondida não consulta; ao voltar e
  ao reconectar (`online`), consulta na hora. O servidor segue sendo a fonte da verdade —
  o hook guarda a chave da consulta (`beachId` ou `__map__`) e nunca exibe, nem por um
  quadro, o dado da praia anterior. `Cache-Control` é 30 s no mapa e 60 s na ficha.
- PWA: service worker manual **nunca** cacheia `/api/**`, `/tiles/**`, WebSocket, não-GET
  nem rotas autenticadas. Nginx e Vite fazem proxy de `/api` e `/ws` (Upgrade).
- **App mobile (ADR-019)**: mesmo `web/` em WebView (Capacitor 8), sem fork de UI;
  `shared/lib/platform.ts` e `shared/lib/native.ts` são os únicos que importam
  `@capacitor/*`; no nativo o `api-client` usa `CapacitorHttp` e o cookie é tratado pelo
  `CookieHandler` do processo. O backend **não muda** por causa do app (sem CORS,
  `SameSite=Strict` intacto); `REALTIME_ALLOWED_ORIGINS` precisa listar
  `https://localhost`/`capacitor://localhost`. `mobile/` (Flutter) está congelado.

## Fatia 1 — importador de áreas de banho (KML → PostGIS)

**Status:** commitada (`1a795ea`, `1466485`, `cf58aa2`, `e08e3e2`). Entregue:
`KmlFeatureReader`, `BathingAreaImportService`, `BathingAreaRepository`, `V11`, endpoint
da coordenação, seed de dev e `ImportMapPage` em `/gestao`. O fluxo é **síncrono** nesta
fatia; a fila (Rabbit ou tabela de jobs) entra quando houver volume/concorrência medidos.

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

## Fatia 2 — mapa público real, arquivamento e ocupação

**Status:** commitada (`e2012a7`, `6dd5097`, `30dccf6`, `403083a`, `7da31af`). Substituiu
os dados mocados de praias pelos dados da importação.

### Arquivamento do KML aplicado (`V12`)

- Lote **aplicado** (nunca `dry-run`) envia os bytes originais ao storage de objetos via
  `S3ObjectStorage` (`ObjectStoragePort`, provider `maraberto.storage.provider=s3`, padrão
  no Compose). Chave por conteúdo: `<prefixo>/<UF>/<sha256>.kml` (padrão
  `bathing-areas/SE/<sha256>.kml`), gravada em `import_batches.source_storage_key`.
- Reenviar o mesmo arquivo reusa o mesmo objeto; **best-effort**: storage fora do ar não
  bloqueia o mapa (coluna fica nula, aviso no log). Credenciais de exemplo do Garage são
  recusadas no perfil `prod` (`S3StorageConfiguration`). Testes fixam `stub`.
- **Gotcha do Garage:** o SDK v2 assina o corpo em `aws-chunked` por padrão e o Garage
  responde `400 Invalid payload signature`. O cliente e o presigner usam
  `chunkedEncodingEnabled(false)` e `requestChecksumCalculation=WHEN_REQUIRED`; sem isso
  todo upload falha mesmo com credencial correta.

### Lista pública por proximidade (RF01)

- `GET /api/v1/beaches?lat=&lon=&radiusM=` — só as áreas ativas num raio de até
  **30 000 m** (`radiusM` omitido = 30 km), da mais próxima para a mais distante; sem
  posição não existe lista. `GET /api/v1/beaches/{id}` devolve a ficha com os setores na
  ordem do mapeamento (`sectorLabel` mostra "Setor N" quando o nome é nulo).
- Lê da projeção materializada `beach_map_features` (`feature_kind = 'AREA'`), com
  `ST_DWithin(geometry::geography, ponto, raio)` sobre o índice GiST de expressão da
  `V13`; distância em metros até o polígono. Erros em RFC 9457 (`localizacao-invalida`
  400, `praia-nao-encontrada` 404). `SecurityConfig` já libera `GET /api/v1/beaches/**`.
- Cache em memória (`BeachMapQueryService`): TTL de 120 s, chave pela posição arredondada
  a 3 casas (~110 m) + raio, **nunca** persistida/logada, limpa por
  `BathingAreaMapChanged`/`BeachChanged`; `Cache-Control: private, max-age=60`.
- Frontend: `useNearbyBeaches` (leitura pontual de GPS, sem `watchPosition`),
  `BeachesPage`/`BeachDetailPage`/`BeachSheet` consumindo a API; `demo-beaches.ts`
  removido. Negar a permissão ou estar longe do litoral mostra estado explicado com
  "Tentar de novo" — a tela não quebra.

### Ocupação anônima (RF10/RN06/RNF04, `V14`)

- `POST /api/v1/occupancy/samples {latitude, longitude, token}` resolve o **setor** que
  contém o ponto (`ST_Contains`) e grava só `beach_id`, `sector_id`, `bucket_start`
  (janela de 15 min) e o **HMAC** do token (`BlindIndexer`). A coordenada é descartada;
  não há coluna de posição, IP nem histórico por aparelho. Fora de setor mapeado → `204`.
- `UNIQUE (anonymous_token, sector_id, bucket_start)` + `ON CONFLICT DO NOTHING`:
  recarregar a tela não infla. Estimativa = `count(DISTINCT token)` na janela móvel;
  abaixo de `minimum-sample` (5) → `INSUFFICIENT` **sem nível** (protege praia vazia).
  Retenção de 24 h com expurgo **na escrita**.
- `GET /api/v1/occupancy/beaches/{id}` devolve `status`, `level` e `windowMinutes`, nunca
  a contagem. Token do cliente é `crypto.getRandomValues` em memória do módulo (rotaciona
  ao recarregar), nunca em storage web. `SecurityConfig` libera `/api/v1/occupancy/**`.

## Fatia 3 — geometria no mapa e ocupação por setor

**Status:** implementada no working tree (backend + testes + frontend + docs); falta o aval
para commitar. **Plano detalhado:** `docs/plan/fatia-3-mapa-e-ocupacao.md` (2026-11-12).

A geometria **já era armazenada** (`beaches.boundary`, `beach_sectors.geometry`, projetada em
`beach_map_features`), mas o cliente só recebia o centroide e a ocupação só agregava por área.
A fatia fechou as três lacunas:

- **Geometria na API:** `GET /api/v1/beaches/{id}/map` devolve `bbox` (`ST_Envelope` em tempo
  de consulta) + geometria GeoJSON simplificada (`ST_SimplifyPreserveTopology`, tolerância
  `5e-5` ≈ 5 m, 6 casas) da área e de cada setor ativo, com `Cache-Control: max-age=60,
  public` e `ETag` por área e por versão do mapa → `304` com `If-None-Match` (aceita lista,
  `*` e `W/`). **`import_batches.id` é UUID**, então a versão é
  `count(*) || '-' || extract(epoch from max(imported_at))` dos lotes aplicados; o `ETag`
  inclui o `beachId` para o `If-None-Match` de uma praia nunca responder `304` para outra.
  A lista continua com centroide; não existe GeoJSON por viewport nesta fatia.
- **Onde estou:** a resposta de `GET /beaches?lat&lon` ganhou `position`
  (`beachId`/`beachName`/`sectorId`/`sectorLabel`), resolvido por `ST_Contains` em
  `beach_sectors` com `ORDER BY display_order, id LIMIT 1` (setor sobreposto → vence o
  primeiro da ordem do mapeamento, a mesma regra da amostra). A query vive em `beaches`
  (duplicada de propósito: o módulo não pode importar `occupancy`) e `sectorLabel` é gerado no
  backend ("Setor N" quando o nome é nulo).
- **Ocupação por setor:** `GET /api/v1/occupancy/beaches/{id}` devolve `BeachOccupancy` —
  faixa da área e `sectors[]`. **A área é `count(DISTINCT token)`, nunca a soma dos setores**
  (quem está na fronteira aparece em dois); `countDistinctTokensBySector` usa `LEFT JOIN`
  para listar setor ativo sem amostra com contagem 0. `k` mínimo continua 5 nos dois escopos;
  setor pequeno responder `INSUFFICIENT` é o esperado. `POST /samples` continua devolvendo só
  a faixa da área.
- **Mapa:** `MapView` recebe `beachArea`/`beachSectors` (GeoJSON genérico) e `focus`
  (`MapFocus`); camadas `beach-area-*`/`beach-sector-*` criadas uma vez no `load` e
  alimentadas por `setData`, enquadramento por `fitBounds` (padding 48, `maxZoom: 16`,
  duration 600) que **não** voa se o `getBounds()` já contém o bbox. Marcador intocado: o
  elemento raiz continua recebendo o `transform` do MapLibre.
- **Deep link e ficha:** a praia selecionada mora na URL (`/?praia=<id>&setor=<id>`), não em
  estado paralelo — "Ver no mapa" da lista e da ficha navega para lá e o mapa enquadra. A
  ficha mostra "Você está no <setor>", a faixa da área e a de cada setor, e o setor
  selecionado vai para `?setor=`. Geometria fica em cache de módulo no frontend (sem TTL;
  muda só em importação) e o `304`/`ETag` fica no servidor (`apiRequest` não envia
  `If-None-Match`).

Sem migração nova (`V15` continua livre para ocorrências): `bbox` sai de `ST_Envelope` em
tempo de consulta e as amostras já têm `sector_id`. PostGIS emite `"type":"Polygon"` quando o
`MultiPolygon` tem uma única parte — testes e tipos do frontend aceitam os dois.

## Fatia 4 — sinalização e estruturas operacionais

**Status:** implementada no working tree (backend + testes + frontend + docs), pendente de
aval para commitar. **Decisão:** ADR-022 · **migração:** `V15`. Substituiu os dados mocados
de postos, bases e bandeiras por leitura real, com ciclo de vida explícito.

### O que está no ar

- **Bandeira (RF12/RN01)**: `POST /api/v1/operations/flags` (guarda-vidas e coordenação) e
  `POST /api/v1/operations/flags/{id}/close`. Corpo com `beachId`, `sectorId` opcional
  (nulo = praia inteira), `flag`, `notes` e `validityHours` (0,5 a 24, padrão
  `maraberto.flags.validity: PT12H`). Lançamento novo no mesmo escopo fecha o anterior sob
  `pg_advisory_xact_lock(hashtextextended(beach_id || ':' || sector_id))`.
- **Mapa público**: `GET /api/v1/flags` devolve só o vigente (`closed_at IS NULL AND
  valid_until > now()`), com nome da praia, rótulo do setor e o pin no centroide do setor
  (ou `ST_PointOnSurface(boundary)` na praia) — **nunca** a posição do operador (RN17).
- **Ficha da praia**: `GET /api/v1/flags?beachId=<id>&includeExpired=true` devolve o último
  lançamento de cada escopo, mesmo vencido/encerrado, para o selo "desatualizada". Sem
  `beachId`, `includeExpired` é `400` em RFC 9457.
- **Estruturas**: `GET /api/v1/stations` (público: só `active`, base móvel só com plantão
  aberto e lease válido), `GET /api/v1/admin/stations` (TI, inclui inativas) e
  `PATCH /api/v1/admin/stations/{kind}/{id}` com `{active, statusNote}` (`kind` =
  `FIXED`/`MOBILE`, `ADMIN`). Reativar limpa `deactivated_at` e **preserva** a nota.
- **Zonas de risco**: `GET /api/v1/risk-zones` publica só `active` e vigente; `PERMANENT`
  nunca expira, `SEMI_PERMANENT` expira em `valid_until`.

### Frontend

- `features/signals` (`useSignals`, `useBeachSignals`, `SignalSheet`, `BeachFlagCard`,
  `SignalStateTag`) e `features/stations` (`usePublicStations`, `useAdminStations`,
  `StationList`, `StationAdminTable`, `StationSheet`) são as features reais; `demo-signals`,
  `demo-stations`, `StationRegistry` e `lib/risk-zones.ts` foram removidos.
- `/operador/sinalizacao` lança e encerra bandeira (praia/setor, validade, observação) e
  lista o último lançamento de cada escopo; `/operador/postos` cadastra, reposiciona e
  ativa/desativa a estrutura do plantão; `/admin/postos` alterna qualquer estrutura e
  corrige a posição; a ficha da praia mostra a bandeira vigente ou "desatualizada".
- `RootLayout` compõe os marcadores a partir de `useSignals`/`usePublicStations`; o
  `SignalsState`/`StationsState` mantém a chave da consulta para não exibir dado da praia
  anterior durante o carregamento.

### Dívidas conhecidas

- **Sem contadores de sinalização** por operador/praia/setor no painel da coordenação (RF40).
- **Push com o app fechado** continua dependendo de projeto Firebase (`google-services.json`).

A foto da sinalização e o cadastro/reposição de estrutura fecharam na Fatia 5 (ADR-023).

## Fatia 5 — sinalização e estruturas ancoradas no aparelho (`V16`, ADR-023)

**Status:** implementada no working tree (backend + testes + frontend + docs), pendente de
aval para commitar. **Decisão:** ADR-023 · **migração:** `V16`. Duas formas de sinalizar e de
cadastrar estrutura, com foto servida pelo backend.

### O que está no ar

- **Duas formas no mesmo endpoint:** `POST /api/v1/operations/flags` aceita `beachId`
  (+ `sectorId` opcional) **ou** `latitude`/`longitude` (+ `accuracyMeters`). Sem nenhuma das
  duas → `400`; só uma coordenada → `400`.
- **Resolução por ponto:** `FlagService.resolveScope` usa `ST_Contains` em `beach_sectors`
  (`ORDER BY display_order, id LIMIT 1`) e, sem setor, `beaches.boundary`. Fora do
  mapeamento, desde a Fatia 7, o lançamento vai para a fila de áreas não identificadas
  (`outcome=UNMAPPED`); a recusa `422` foi removida (ADR-024).
- **Posição da sinalização:** `flag_updates.signal_position`/`position_accuracy_m`/
  `position_source` (`SCOPE`/`DEVICE_GPS`); pin público =
  `COALESCE(signal_position, centroide do setor, ST_PointOnSurface(área))`. Um ponto por
  lançamento, **sem histórico de trajeto**.
- **Foto (RF12/ADR-012):** `POST /api/v1/media` multipart **ou** JSON `{contentBase64}`
  (o app usa sempre base64); `ImageNormalizer` re-codifica em JPEG (maior lado 1600 px,
  qualidade 0,85, teto de 40 MP, EXIF/GPS fora); `media_assets` + `flag_media` (até 3, com
  `display_order`). `MediaCatalog` confere existência e **dono** antes de anexar.
  `GET /api/v1/media/{id}` é público, com `ETag` do SHA-256, `If-None-Match`/`304` e
  `Cache-Control: public, max-age=30d, immutable`. `FlagResponse`/`PublicFlag` levam
  `photoUrls`.
- **Estruturas (RF11):** `POST /api/v1/operations/stations` (cadastro na posição atual) e
  `PATCH /api/v1/operations/stations/{kind}/{id}/position` (reposição), para
  `LIFEGUARD`/`COORDINATOR`/`ADMIN`; `position_source`/`position_accuracy_m`/
  `position_updated_at` em `lifeguard_posts`/`mobile_bases`; `StationAdminView` expõe
  origem/precisão/data. A TI continua com o cadastro administrativo; a ativação em campo
  e a foto do posto chegaram na Fatia 8 (ADR-024).

### Frontend

- `/operador/sinalizacao` tem dois modos (`Praia e setor` / `Minha localização`), até 3 fotos
  com prévia, progresso de envio e aviso de validade; `shared/lib/media.ts` faz o upload em
  base64 com `photoProblem`/`MAX_SIGNAL_PHOTOS`, e `SignalPhotos` mostra galeria + dialog na
  ficha, no card da praia e na lista do console.
- `/operador/postos` deixou de ser somente leitura: `NewStationForm` cadastra posto/base na
  posição atual (`useStationPosition` → `getDevicePosition`) com até 3 fotos, e
  `/admin/postos` ganhou "Corrigir posição" (`StationPositionForm`) com a origem e a
  precisão na linha.
- `shared/components/PhotoGallery.tsx` é a galeria única (imagem inteira, anterior/próxima,
  contador, miniaturas, setas e `Escape`); `SignalPhotos` é o wrapper da sinalização e a
  mesma galeria aparece em `StationList`/`StationSheet`/`StationAdminTable`.
- `shared/lib/api-client.ts` expõe `apiAssetUrl` para a `<img>` funcionar no app (a API é
  outra origem e a tag não passa pelo cliente HTTP).

### Dívidas conhecidas

- Upload base64 cresce ~33% no app; limites de multipart em 12 MB (arquivo) / 14 MB (request).
- Foto enviada e não anexada fica órfã (inofensiva) até uma rotina de retenção.
- Ajuste fino do pino e desenho da bandeira na foto continuam para uma fatia de UX.

## Fatia 6 — ocorrência aberta no aceite do SOS (`V17`, ADR-013 emendada)

**Status:** implementada no working tree (backend + testes + frontend + docs), pendente de
aval para commitar. **Decisão:** ADR-013 emendada · **migração:** `V17`. Escopo desta fatia:
**nascer e ler** a ficha; encerramento, desfecho e protocolo CIOSP ficam para a próxima.

### Backend

- **Gatilho é o ack:** `emergency/api/SosAcknowledged` (`@NamedInterface("api")`) é publicado
  no aceite; `SosOccurrenceListener` roda `AFTER_COMMIT` + `REQUIRES_NEW` e abre a ficha
  (`origin=SOS_CITIZEN`, `status=IN_PROGRESS`, `ciosp_status=PENDING`). Chamado sem ack não
  gera ocorrência; falha do listener só loga.
- **Idempotência e rede de segurança:** `INSERT … SELECT … ON CONFLICT (emergency_call_id) DO
  NOTHING`; `OccurrenceReconciliationJob` abre a ficha de todo chamado assumido há mais de
  `maraberto.occurrences.reconciliation.*` e sem par. `emergency` não depende de
  `occurrences` e o SOS nunca espera a ficha.
- **Leitura (RN15):** `GET /api/v1/occurrences` e `GET /{id}`; `LIFEGUARD` vê o que está no
  escopo do seu plantão aberto (posto/praia) e o que assumiu; `COORDINATOR`/`ADMIN` veem
  tudo; fora do escopo → `404`. Erros em RFC 9457.

### Frontend

- `features/occurrences` (types/api/hook de 30 s/lista) alimenta `/operador/ocorrencias` e
  `/gestao/ocorrencias`; a ficha aparece depois do ack, sem recarregar a página.

### Dívidas conhecidas

- Encerramento com `outcome_type`, transição a partir de `RESOLVED`/`FALSE_ALARM`,
  `FIELD_ATTENDED` e ficha/protocolo do CIOSP ainda não existem.

## Fatia 7 — sinalização fora do mapeamento em triagem (`V18`, ADR-024)

**Status:** implementada no working tree (backend + testes + frontend + docs), pendente de
aval para commitar. **Decisão:** ADR-024 · **migração:** `V18`. Ponto fora do KML não é
recusado: vira registro para a coordenação decidir o que fazer.

### Backend

- `POST /api/v1/operations/flags` devolve `FlagLaunchResponse`: `outcome=PLACED` (bandeira
  publicada) ou `outcome=UNMAPPED` (`201`, registro na fila, `flag` nulo).
- `unmapped_signals` guarda um ponto por lançamento (cor pretendida, observação, precisão,
  matrícula de quem reportou, posto do plantão) e `unmapped_signal_media` as até 3 fotos.
  **Nada entra em `flag_updates`** e `GET /api/v1/flags` não lê a fila.
- `GET /api/v1/management/unmapped-signals?status=&limit=` e
  `POST /{id}/decision` (`COORDINATOR`; `ADMIN` como superusuário técnico). Decisões
  `IDENTIFIED`/`NEEDS_MAPPING`/`DISCARDED`/`PENDING`; `IDENTIFIED` exige área ativa e setor
  da área quando informado. A linha nunca é apagada.
- A área ativa mais próxima é sugestão calculada na consulta (`ST_Distance`), nunca vínculo
  automático. A fila é sensível (RN17): coordenada e matrícula só na rota autenticada.
- `GET /api/v1/management/bathing-areas` é o catálogo enxuto (id, nome, município, setores)
  usado pelo seletor da triagem.

### Frontend

- `features/signals` expõe `LaunchFlagResult`/`UnmappedSignal`; `launchFlag` devolve o
  desfecho e `/operador/sinalizacao` mostra "registro enviado para a coordenação" sem fingir
  que publicou bandeira.
- `/gestao/areas-nao-identificadas` (`ManagementUnmappedSignalsPage` + `useUnmappedSignals`)
  lista a fila, sugere a área mais próxima e grava a decisão; item de nav com `MapPinOff`.

### Dívidas conhecidas

- Sem notificação de novo registro para a coordenação; a fila é consultada na tela.
- Identificar na triagem **não** publica bandeira retroativa: a decisão só associa o local.

## Fatia 8 — estrutura em campo e foto do posto (`V19`, ADR-024)

**Status:** implementada no working tree (backend + testes + frontend + docs), pendente de
aval para commitar. **Decisão:** ADR-024 · **migração:** `V19`. O guarda-vidas gerencia a
estrutura do próprio plantão e a população vê a foto do posto.

### Backend

- `PATCH /api/v1/operations/stations/{kind}/{id}` (`{active, statusNote}`): `LIFEGUARD` só
  alterna a estrutura do plantão aberto dele (fora → `403` `station-outside-shift`);
  `COORDINATOR`/`ADMIN` alternam qualquer uma. Desativar carimba `deactivated_at` e a nota;
  reativar limpa a data e preserva a nota. Nada é apagado.
- `GET /api/v1/operations/stations/{kind}/{id}` devolve a estrutura mesmo desativada.
- `CreateStationRequest.mediaIds` (até 3) anexa `media_assets` via `lifeguard_post_media` /
  `mobile_base_media` (`V19`); `photoUrls` aparece em `PublicStation` e `StationAdminView`.
  O upload e a normalização são os mesmos da sinalização.

### Frontend

- `ShiftStationCard` em `/operador/postos` lê o plantão (`useShift`), busca a estrutura e
  oferece desativar (com motivo opcional) ou reativar; o pin sai do mapa na hora.
- `StationList`, `StationSheet`, `StationAdminTable` e `NewStationForm` mostram/enviem fotos
  com a `PhotoGallery` compartilhada.

### Dívidas conhecidas

- Não há troca de foto depois do cadastro nesta fatia (só novo cadastro/reposição anexa).
- A galeria não tem zoom por gesto; a foto órfã segue sem rotina de retenção.

## Roadmap conhecido (curto)

- `beaches` — depois da Fatia 3: GeoJSON público por viewport com ETag/Redis e
  importação de postos/bases/zonas (as zonas e a leitura pública por área já existem).
- `occurrences` — `V20`: encerramento/desfecho, `FIELD_ATTENDED`, ficha e protocolo CIOSP,
  atualização de status a partir de `RESOLVED`/`FALSE_ALARM` (ADR-013).
- `operations` — `rip_current_markings` para correntes efêmeras (RF13); contadores de
  sinalização no painel da coordenação (RF40).
- `region` — `installation_settings` e Region Pack.
- `tides`/`content`/`search` — endpoints públicos de marés/vídeos e `pg_trgm` (ADR-014).
- `emergency` — transição `RESOLVED`/`FALSE_ALARM`, perfil `rabbit` com DLQ, Web Push
  com o app fechado, métricas/k6 (gatilhos da ADR-020).
- Frontend — painel da gestão com posição e contadores de sinalização,
  formulário de ocorrência, tiles PMTiles e ícones padronizados.
- App mobile — câmera/push, ícone/splash próprios, keystore e teste em aparelho físico.
- CI — lint/build do frontend, Testcontainers Garage, SAST/SCA e cobertura.
