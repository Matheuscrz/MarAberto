# Fatia 3 — geometria no mapa e ocupação por setor

**Status:** implementada (backend + testes + frontend + docs); falta o aval para commitar.
**Data:** 2026-11-12.
**Relacionado:** ADR-016 (importação), ADR-021 (área de banho), RF01, RF10, RN06, RNF04.

Objetivo de três partes:

1. **Expor a geometria** que o importador já guarda (área e setores) para o mapa desenhar
   o polígono, e não só o centroide.
2. **Dizer ao banhista onde ele está**: em qual área e em qual setor daquela área, a partir
   da posição que ele já envia hoje.
3. **Calcular ocupação da área e do setor** com as mesmas amostras anônimas, e ao selecionar
   uma praia (ou um setor) **enquadrar o mapa** naquele polígono, como o Maps faz.

---

## 1. O que já existe (não refazer)

| Peça | Onde | Situação |
| :-- | :-- | :-- |
| Geometria da área | `beaches.boundary` (`geometry(MultiPolygon,4326)`) | preenchida na importação como **união dos setores** |
| Geometria do setor | `beach_sectors.geometry` (`MultiPolygon,4326`) | uma feição do KML por setor |
| Projeção de leitura | `beach_map_features` (V11) | `AREA` + `SECTOR` com `geometry`, `active`, nome, município |
| Índices | V11 e V13 | GiST em `geometry` e GiST de expressão em `geometry::geography` |
| Refresh | `@TransactionalEventListener(AFTER_COMMIT)` + `REFRESH ... CONCURRENTLY` | a view só muda em lote aplicado |
| Lista/ficha | `PublicBeachRepository`, `BeachMapQueryService` | devolvem **centroide** (`ST_Centroid`), nunca a geometria |
| Ocupação | `OccupancyService` / `OccupancyRepository` | grava `beach_id` + `sector_id` + janela + HMAC; estima **só por área** |
| Mapa | `web/src/features/map/components/MapView.tsx` | fontes `risks` e `user`; **sem camada de praia** e sem enquadramento |
| Seleção | `RootLayout` + `BeachSheet` | praia não tem marcador; abre a ficha, não voa para o polígono |

Ou seja: **o dado geográfico já está no banco**. O que falta é contrato de saída, uso no
cliente e a agregação por setor.

## 2. Lacunas a fechar

1. `GET /api/v1/beaches` e `/beaches/{id}` não devolvem `bbox` nem `geometry`; o cliente não
   consegue desenhar nem enquadrar sem carregar o KML (que não é servido).
2. Nada informa "você está no Setor Dunas da Praia da Cinelância". A resolução de setor
   existe, mas só no caminho de escrita da amostra.
3. `GET /api/v1/occupancy/beaches/{id}` devolve apenas a faixa da área; não há leitura por
   setor, e o `OccupancyRepository` conta por `beach_id`, sem `GROUP BY sector_id`.
4. `MapView` não tem camada de área/setor nem forma de o app mandar "enquadre aqui".

## 3. Decisões propostas

### 3.1 Geometria: endpoint dedicado, não engordar a lista

A lista por proximidade é chamada a cada abertura do mapa com a posição do usuário; ela não
pode carregar 18 polígonos. Decisão:

- **`GET /api/v1/beaches/{id}/map`** devolve a área + os setores com `bbox` e geometria
  **simplificada** (`ST_SimplifyPreserveTopology`, tolerância padrão `5e-5` ≈ 5 m,
  `ST_AsGeoJSON(..., 6)`), com `ETag` e `304`.
- A lista e a ficha continuam com centroide (contrato atual, já testado).
- **Não** criar por enquanto um GeoJSON por *viewport* (`?bbox=`). 32 áreas em SE cabem
  numa resposta por praia; o endpoint de viewport entra quando houver muitos estados ou
  quando o mapa precisar mostrar todas as áreas de uma vez (documentado em §7.7).
- **Sem migração**: `bbox` sai de `ST_XMin/ST_YMin/ST_XMax/ST_YMax(ST_Envelope(geometry))`
  em tempo de consulta (45 linhas, custo irrelevante). `V15` continua livre para
  ocorrências.

`ETag` = `<versão>-<tolerância>`, onde a versão é `max(id)` de `import_batches` com
`applied = true` — monotônica e exatamente o instante em que a view é recarregada.
`If-None-Match` igual → `304` sem corpo. `Cache-Control: public, max-age=60`.

### 3.2 "Onde estou": resolvido no servidor, dentro da lista

A posição já viaja em `GET /api/v1/beaches?lat&lon&radiusM`; não criar um segundo request
só para isso. A resposta ganha um bloco:

```json
{
  "radiusMeters": 30000,
  "position": {
    "beachId": "0387a4a7-…",
    "sectorId": "541f723d-…",
    "sectorLabel": "Setor Dunas",
    "beachName": "Praia da Cinelância"
  },
  "beaches": [ … ]
}
```

- `position` é `null` quando o ponto não está em nenhum setor ativo.
- Resolução: `ST_Contains(geometry, ponto)` em `beach_sectors`, `ORDER BY display_order
  LIMIT 1` (setores sobrepostos → vence o primeiro; mesma regra da amostra de ocupação).
- **A query fica em `beaches`**, duplicando a de `occupancy` de propósito: o módulo `beaches`
  não pode importar `occupancy` (fronteira do Modulith) e a duplicação é uma linha de SQL
  sobre as tabelas do próprio módulo.
- `sectorLabel` já vem pronto ("Setor N" quando o nome é nulo), para o cliente não repetir
  a regra de rótulo.

Privacidade inalterada: a coordenada **não** é persistida nem logada; o cache da lista
continua com a chave arredondada a 3 casas.

### 3.3 Ocupação por setor: mesma amostra, duas leituras

A amostra já grava `sector_id`; falta só agrupar na leitura.

- `OccupancyRepository.countDistinctTokensBySector(beachId, since)` →
  `SELECT sector_id, count(DISTINCT anonymous_token) … GROUP BY sector_id`.
- `GET /api/v1/occupancy/beaches/{id}` passa a devolver, **de forma aditiva**:

```json
{
  "beachId": "0387a4a7-…",
  "status": "ESTIMATE",
  "level": "LOW",
  "windowMinutes": 15,
  "sectors": [
    { "sectorId": "541f723d-…", "status": "ESTIMATE", "level": "LOW" },
    { "sectorId": "eb34665b-…", "status": "INSUFFICIENT", "level": null }
  ]
}
```

- **Regra dura: a área é `count(DISTINCT token)` da área, nunca a soma dos setores.** Quem
  está na fronteira de dois setores aparece nos dois; a união deduplica. Isso precisa estar
  no JavaDoc, no OpenAPI e num teste.
- `k` mínimo continua **global (5)** e sem nível abaixo dele. Não criar `sector-minimum-sample`
  menor "para o setor aparecer": com menos gente a faixa permitiria inferir contagem. Setor
  pequeno responder `INSUFFICIENT` é o comportamento correto, não bug.
- `POST /samples` continua devolvendo só a faixa da área (o cliente já sabe o setor pelo
  bloco `position` da lista); evita dois contratos dizendo coisas parecidas.

### 3.4 Mapa: enquadrar é efeito, nunca recriar o mapa

`MapView` ganha uma prop de foco e duas camadas:

```ts
interface MapFocus {
  readonly bbox: readonly [number, number, number, number]; // [minLon, minLat, maxLon, maxLat]
  readonly kind: "BEACH" | "SECTOR";
  readonly id: string;
}
```

- Efeito de foco: `map.fitBounds(bbox, { padding: 48, maxZoom: 16, duration: 600 })`.
  **Não** voar se `map.getBounds().contains(bbox)` — quem já está vendo a praia não deve
  perder o zoom; nesse caso só destacar.
- Camadas novas, no mesmo padrão de `risks`: `beach-area-fill`/`beach-area-line` e
  `beach-sector-fill`/`beach-sector-line`, alimentadas por `setData` a partir do GeoJSON do
  endpoint `/map`. Setor selecionado ganha opacidade maior; área selecionada ganha contorno.
- **Regra que continua valendo:** os marcadores são criados uma vez e atualizados por
  classe; o elemento raiz do marcador recebe `transform` do MapLibre e não pode ser tocado.
  Enquadramento é `fitBounds` na instância do mapa, não CSS.
- Se o mapa for recriado (troca de tema/WebGL), reaplicar o foco depois do `load`.

Fluxo de seleção no app (`RootLayout`):

1. Lista (`BeachesPage`) → seleciona praia → `setSelectedBeachId(id)` + `setFocus({bbox})` +
   abre `BeachSheet`.
2. `BeachSheet` → "Ver no mapa" (na ficha de `/praias/:beachId`, navega para
   `/?praia=<id>`; **não** criar um segundo mapa na página de detalhe).
3. Setor clicado (na ficha ou no detalhe) → `focus` no `bbox` do setor e destaque; o texto
   "Você está no Setor N" aparece quando `position.sectorId` bate.
4. Toque no mapa fora de tudo → limpa seleção e foco (comportamento atual de `onSelectMarker(null)`).

### 3.5 Cache e payload

- Geometria completa fica no banco; o cliente recebe simplificada (`5e-5`) com 6 casas.
- `ETag` por versão do mapa + tolerância; `304` evita re-render e tráfego no app.
- Lista por posição: mantém o cache em memória de 120 s já existente (posição arredondada);
  `position` entra na mesma chave.
- Ocupação: **sem cache** (janela de 15 min; barato e sensível).

### 3.6 Índices

`occupancy_samples` já tem `(beach_id, created_at DESC)`, que atende a agregação por área.
Para `GROUP BY sector_id` dentro da área, **medir antes**: `EXPLAIN (ANALYZE, BUFFERS)` com
volume realista; candidato `(beach_id, sector_id, created_at)`. Não criar índice "por
garantia" — a tabela é pequena (retenção de 24 h).

## 4. Passos sugeridos (ordem de execução)

### Backend

1. `PublicBeachRepository`: `findMap(UUID)` com `bbox` + `ST_AsGeoJSON(ST_SimplifyPreserveTopology(...))`;
   `findPositionAt(lat, lon)` (`ST_Contains` em `beach_sectors`).
2. DTOs em `beaches/api`: `BeachMap`, `BeachMapSector`, `PositionContext`; `NearbyBeaches`
   ganha `position`; `BeachMapQueryService` devolve `ETag` e aplica o cache.
3. `PublicBeachController`: `GET /api/v1/beaches/{id}/map` com `If-None-Match` → `304`.
4. `OccupancyRepository.countDistinctTokensBySector` + `OccupancyEstimate.sectors`.
5. OpenAPI (`@Schema`, `@ApiResponse` incluindo 304) e RFC 9457 nos erros novos.

### Testes

- `PublicBeachQueryIntegrationTests`: bbox contém a geometria; geometria não vazia e
  `ST_IsValid`; `position` dentro (setor correto), fora (`null`), e setores sobrepostos
  (vence o de menor `display_order`); `ETag` estável entre requisições e muda depois de um
  lote aplicado; `304` com `If-None-Match`.
- `OccupancyIntegrationTests`: contagem por setor; **união ≠ soma** (mesmo token em dois
  setores conta 1 na área e 1 em cada setor); setor abaixo de k → `INSUFFICIENT`.
- `ModularityTests` verde (garantir que `beaches` não importa `occupancy`).

### Frontend

6. `web/src/features/beaches/api/beaches.ts`: `fetchBeachMap(beachId)`; tipos em `types.ts`.
7. `web/src/features/beaches/api/occupancy.ts`: `sectors` na estimativa.
8. `MapView`: prop `focus`, camadas de área/setor, destaque do setor atual.
9. `RootLayout`/`BeachSheet`/`BeachDetailPage`: seleção → foco, "Ver no mapa", setor clicável,
   selo "Você está aqui".

### Docs

10. `docs/Architecture.md` §7.7 (contrato `/map` + ETag), §8.5 (ocupação por setor e regra da
    união), §9 (cache do GeoJSON); `AGENTS.md` (estado, endpoints e orçamento de payload).

## 5. Critérios de aceite

- Selecionar uma praia na lista centraliza o mapa no polígono dela (bbox + padding) e
  destaca a área; selecionar um setor enquadra **apenas** aquele setor.
- Quem já está com a praia enquadrada não perde o zoom ao selecionar de novo.
- `GET /api/v1/beaches/{id}/map` devolve `MultiPolygon` válido em 4326, `bbox` e `ETag`;
  segundo request com `If-None-Match` responde `304` sem corpo.
- `GET /api/v1/beaches?lat&lon` informa `position` com `beachId`/`sectorId`/`sectorLabel`
  quando o aparelho está dentro de um setor ativo; fora, `position: null`.
- `GET /api/v1/occupancy/beaches/{id}` devolve área + setores; a área nunca é a soma dos
  setores; abaixo de 5 amostras, `INSUFFICIENT` sem nível.
- Nenhuma coordenada de banhista no banco, no log ou em cache persistido.
- `./gradlew check`, `npm run lint` e `npm run build` verdes.

## 6. Riscos e limitações assumidas

- **Setores sobrepostos:** vence o de menor `display_order`; documentar no JavaDoc e cobrir
  em teste. Não tentar "resolver" sobreposição em runtime.
- **`beaches.boundary` é a união dos setores:** pode ter buracos e não é a delimitação
  oficial; para enquadrar um setor, usar o bbox **do setor**, que é mais preciso.
- **Ocupação por setor** tende a ficar `INSUFFICIENT` com pouca gente. É honesto: a
  alternativa (k menor) permite inferir quantas pessoas estão num setor vazio.
- **Payload:** a simplificação é por praia; se o KML crescer (muitos vértices), medir e
  ajustar a tolerância por faixa de zoom antes de criar endpoint por viewport.
- **Recriar o mapa** derruba marcadores e foco; qualquer mudança no `MapView` precisa manter
  a criação única e reaplicar o foco no `load`.
- **Sem emulador nesta rodada:** validação manual no aparelho do usuário.

## 7. Fora de escopo (depois)

- `GET /api/v1/beaches/geojson?bbox=` com ETag por viewport e CDN.
- Importação de postos, bases e zonas de risco pelo mesmo pipeline.
- Mapa na página de detalhe (hoje o detalhe manda para o mapa principal).
- Ocupação histórica/série temporal — a retenção de 24 h e o `INSUFFICIENT` são deliberados.
