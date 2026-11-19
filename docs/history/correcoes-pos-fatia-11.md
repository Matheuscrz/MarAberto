# Correções pós-Fatia 11

> Registro das correções e melhorias pedidas após a entrega da Fatia 11
> (`cf1d9ef`), concluídas em 2026-11-18. Para o estado operacional vigente,
> consulte `AGENTS.md`, `docs/Architecture.md` e `docs/Requirements.md`.

## Contexto

Os oito commits da Fatia 11 estavam no repositório, mas o uso em campo revelou
três grupos de problema: a tela de equipe da coordenação respondia 404, a ficha
de ocorrência fechava o mapa de situação e o upload de fotos falhava com 500.
Além disso, a lista de postos misturava bases móveis encerradas com estruturas
em operação e não havia como consultar histórico sem carregar tudo.

## 1. Equipe (`/gestao/equipe`) retornando 404

**Causa:** não era código. O contêiner `maraberto-backend` rodava a imagem
construída em 2026-11-17, anterior ao endpoint `GET /api/v1/management/team`.
O código, o contrato e os testes (`ManagementTeamControllerTests`) já estavam
corretos.

**Correção:** reconstruir a imagem (`docker compose build backend && docker
compose up -d backend`) aplica a migration pendente e expõe o endpoint. A
consulta devolve lista vazia — e não 404 — quando não há plantão com lease vivo,
que é o comportamento esperado pela tela.

## 2. Ficha de ocorrência fechava o mapa de situação

**Causa:** o `OccurrenceSheet` era renderizado dentro do contêiner
`pointer-events-none` do mapa de situação (`ManagementSituationPage`). Sem
`pointer-events-auto` no próprio `<dialog>`, o clique atravessava o overlay e
chegava ao link "Mapa público" do `AreaShell`, navegando para `/`.

**Correção:** `pointer-events-auto` no `<dialog>` de `OccurrenceSheet`, o mesmo
padrão já usado em `StationSheet`/`SignalSheet`. A ficha permanece aberta sobre
o mapa e o clique fora fecha só a ficha.

## 3. Upload de mídia (500) e fotos que não apareciam

**Causa raiz:** `.env`/`.env.example` definiam
`STORAGE_S3_ENDPOINT=http://localhost:9000`. Dentro do contêiner, `localhost`
é o próprio backend, então o SDK do S3 tentava conectar em si mesmo e o upload
estourava `SdkException` → 500. O endpoint correto na rede do Compose é
`http://garage:3900`; `localhost:9000` só vale para o backend rodando no host.

**Correções:**

- `.env` e `.env.example` passam a usar `http://garage:3900`, com comentário
  explicando a diferença entre host e contêiner.
- `MediaApiExceptionHandler` trata `SdkException` como **503**
  (`https://maraberto.org/problems/storage-unavailable`) com mensagem de
  "tentar de novo", em vez de 500 genérico. O log registra a causa sem expor
  credenciais.
- O seletor de fotos deixa de exibir o texto nativo "Nenhum arquivo
  selecionado": `NewStationForm` e `OperatorSignalsPage` usam input `sr-only`
  com label estilizado ("Escolher fotos"), foco visível e contador
  "N de 3 adicionadas". Não há mudança no envio: continua base64, com
  re-codificação e remoção de EXIF no servidor.
- As fotos do posto já eram exibidas em `StationList`, `StationSheet`,
  `FieldStationList` e `StationAdminTable`; elas não apareciam porque o upload
  nunca concluía. Com o endpoint corrigido, o vínculo `mediaIds` chega ao
  cadastro e a galeria renderiza.

## 4. Postos: filtro por estado e paginação por cursor

**Backend:** migration `V22__station_catalog_and_query_indexes.sql` cria a view
`station_catalog` (posto fixo + base móvel em uma única leitura, com
`photo_urls`) e índices de catálogo/estado. `GET /api/v1/admin/stations` e
`GET /api/v1/operations/stations` aceitam `active`, `kind`, `q`, `limit` e
`cursor`, e devolvem `StationPage { items, nextCursor, hasMore, total }`. A
ordem é `(kind, name, id)` e o cursor opaco (Base64 de `kind|name|id`) mantém a
página estável mesmo quando uma estrutura é ativada/desativada no meio da
consulta. O total considera o filtro, não a página.

**Frontend:** `StationAdminTable` (gestão e TI) abre em **Ativos**, com abas
Ativos/Inativos/Todos, filtro de tipo, busca por nome com debounce de 350 ms e
"Carregar mais antigas". O contador mostra "X de Y estruturas". A lista do
console de campo (`/operador/postos`) recebe o mesmo filtro de estado pelo
servidor e também abre nos ativos, com "Carregar mais estruturas" para o
histórico. Toda alteração de estado recarrega a página atual para o item entrar
ou sair do filtro. A base móvel encerrada deixa de poluir a lista, mas continua
acessível em Inativos/Todos.

## 5. Ocorrências: filtros e período no servidor

**Backend:** `GET /api/v1/occurrences` passa a aceitar `closed` (só encerradas)
e `ciospPending`, somados a `status`, `active`, `from` e `to` (ISO-8601,
`occurred_at >= from` e `occurred_at < to`). A paginação continua por keyset
`(occurred_at DESC, number DESC)`; a `V22` adiciona os índices correspondentes.
`from >= to` responde 400 com RFC 9457.

**Frontend:** `ManagementOccurrencesPage` manda as abas (Em atendimento,
Encerradas, CIOSP pendente) e o período De/Até para o servidor. Os contadores
são rotulados "Nesta tela" porque refletem a página carregada, não o total do
banco. "Carregar mais antigas" usa o cursor devolvido, sem offset.

## 6. Triagem: abrir o ponto no Google Maps

Cada registro de `unmapped_signals` ganhou o link **"Abrir no Google Maps"**
com as coordenadas do pin (`https://www.google.com/maps?q=lat,lng`, nova aba,
`rel="noreferrer noopener"`). A coordenação confere no satélite se o local já
corresponde a uma área mapeada antes de identificar ou pedir um novo KML.

## Validação

- `./gradlew clean check` — verde.
- `npm run lint` e `npm run build` — verdes.
- `GET /api/v1/management/team` com `coordenador@maraberto.dev` após rebuild da
  imagem: 200 com lista (vazia ou não), nunca 404.
- Upload `POST /api/v1/media` com JPEG/PNG válido: 201 e `GET
  /api/v1/media/{id}` serve a imagem; storage fora do ar responde 503 com
  `application/problem+json`.
- Sinalização e posto com foto: a imagem aparece na ficha e na lista.
