# ADR-021: Área de banho como unidade territorial (praia, rio e barragem)

- Status: **Aceito** (2026-11-11) — validada na Fatia 1 com o mapeamento real de Sergipe: 45 setores, 32 áreas, 20 setores sem nome e 3 barragens importados e reimportados sem mudar UUID (`SergipeMappingAcceptanceTests`).
- Relacionadas: ADR-010 (zonas temporais), ADR-012 (dados sensíveis), ADR-016 (importação QGIS/KML com setores), ADR-017 (geoindexação do SOS).
- Requisitos: RF01, RF02, RF11, RF38, RF41; RN03; RNF18.

## Contexto

O sistema nasceu com o vocabulário de **praia** (`beaches`, `Beach`, `beach_id`), porque o primeiro caso de uso era o litoral. O mapeamento real de Sergipe (`docs/kml/Areas_Banho_Sergipe.kml`) mostra que a operação não é só litorânea:

- **45 feições = 45 setores** de **32 áreas de banho** distintas por (`Municipio`, `Praia`);
- praias oceânicas (Aracaju, Barra dos Coqueiros, Estância, Pirambu), **praias de rio** (Nossa Senhora do Socorro, Propriá, Neópolis, Santana do São Francisco, Canindé, Porto da Folha, Telha, Pacatuba) e **barragens** (Campo do Brito, Lagarto, Cumbe);
- 20 dos 45 setores **não têm nome** (`Setor = "-"`), mas continuam sendo setores — não "a praia inteira";
- o mesmo nome de praia se repete em municípios diferentes ("Prainha da Orla" em Canindé, Propriá e Neópolis).

O modelo atual já resolve a maior parte disso: `beaches` guarda o polígono da área, `beach_sectors` (ADR-016) guarda os trechos operacionais e o resto do sistema referencia `beach_id`/`beach_sector_id`. O que falta é **nomear corretamente a coisa** e permitir classificar o tipo de corpo d'água sem multiplicar tabelas.

## Decisão

1. **"Área de banho" é o conceito de domínio; `beaches` continua sendo o nome físico da tabela e `Beach` o nome da entidade.** Renomear tabela/entidade quebraria FKs, consultas espaciais e o vocabulário já usado em `operations`, `emergency`, `safety` e `occupancy` sem ganho funcional. O `package-info` e os JavaDocs passam a dizer "área de banho (praia, rio, barragem)".
2. **Adicionar `kind` em `beaches`**: `BEACH` (padrão), `RIVER`, `DAM`, `LAGOON`, `OTHER`. É um atributo de classificação, não uma hierarquia de tabelas.
3. **Cada feição do mapeamento é um `beach_sector`**, inclusive quando não tem nome. `beach_sectors.name` é **anulável e repetível** (dois setores da mesma praia podem ter o mesmo nome); a ordem vem de `display_order` e a UI mostra "Setor N" quando não houver nome. Nome de setor é rótulo, nunca identidade: o `UNIQUE` é só em `external_id`, jamais em (`beach_id`, `name`).
4. **A chave natural da área é (`state_code`, `municipio`, `praia` normalizada)** — nome de praia não é único no município, no estado nem no país. O `external_id` de cada feição vem do `id` do KML com prefixo da UF (`<uf>-areas-banho:<id>`), porque o `id` é único só dentro do arquivo; o da **área** usa como âncora o **menor `id` de setor** (`<uf>-areas-banho:area:<min-id>`), não o nome, para que renomear a praia no QGIS atualize a mesma área. Se a âncora sumir, o importador casa por (`state_code`, `municipio`, `praia`) e mantém o UUID. O upsert **preserva o `id` (UUID)** já existente, então atualizar o mapa não quebra as FKs de postos, zonas, plantões e ocorrências. `beaches.state_code` vem do parâmetro `state` informado pelo órgão quando o arquivo não traz `Estado`; divergência entre os dois é rejeitada.
5. **O polígono da área é derivado dos setores** (`ST_Union`), não digitado à mão; o setor é a fonte da verdade geométrica. A soma dos setores não precisa cobrir 100 % da área.
6. **O tipo do corpo d'água não é inferido por heurística frágil.** Ordem: campo `Tipo` no `ExtendedData` do KML (quando o QGIS exportar) → prefixo inequívoco `Barragem`/`Açude` = `DAM` → `BEACH` como padrão. Rio, lagoa e demais casos são ajustáveis no cadastro/endpoint sem reimportar.
7. **Dados de origem que não são estado vivo ficam em `source_metadata JSONB`**, por exemplo `Num. de Oc` (contagem histórica do arquivo). Não viram contador operacional nem entram em métrica.

## Alternativas

- **Tabelas separadas por tipo** (`rivers`, `dams`, `lagoons`): rejeitada. Multiplicaria FKs (`lifeguard_posts`, `critical_zones`, `occurrences`, `shift_sessions`) e consultas espaciais por tipo, sem nenhuma regra de negócio que difira entre praia e barragem.
- **Renomear `beaches` → `bathing_areas`**: rejeitada agora. O ganho é cosmético e o custo é migração de FKs, entidades, consultas nativas e contrato do frontend; o conceito já fica documentado e o `kind` cobre a distinção.
- **Um setor por praia, com o nome da praia como nome do setor**: rejeitada. Perde a setorização real (uma praia com 5 setores) e impede plantão/bandeira/ocorrência por trecho.
- **Inferir o tipo pelo município ou pelo nome**: rejeitada. Seria dado regional embutido no código e erraria em casos como "Prainha" (rio) e "Praia da Costa" (mar).

## Consequências

- **Positivas:** um único modelo atende litoral e interior; o KML real de Sergipe entra sem gambiarra; `beach_sector_id` continua sendo a unidade de plantão, bandeira, ocorrência e ocupação; a classificação é dado, não código.
- **Negativas / dívidas:** `Beach`/`beaches` passam a ter nome enganoso (mitigado por JavaDoc e `kind`); a migração precisa trocar `boundary` de `geometry(Polygon,4326)` para `geometry(MultiPolygon,4326)`; setores sem nome exigem `name` anulável e rótulo calculado na UI; a união dos setores pode gerar `MultiPolygon` com lacunas, o que é aceitável para exibição e geofence.
- **Privacidade:** geometria de área/setor é pública; posição de operador e coordenada de ocorrência continuam sensíveis (ADR-012).
- **Revisão:** concluída na Fatia 1 (2026-11-11): o KML real de Sergipe foi importado e reimportado com os contadores conferidos (45 setores / 32 áreas), setores sem nome e barragens classificadas. Reabrir se aparecer um tipo de corpo d'água que não caiba em `kind`.

## Referências

- `docs/Architecture.md` §7.7 (ingestão), §7.8 (zonas), §7.12 (geoindexação)
- `docs/Requirements.md` RF01, RF02, RF11, RF38, RF41, RN03
- ADR-010, ADR-012, ADR-016, ADR-017
- `docs/kml/Areas_Banho_Sergipe.kml` (mapeamento real usado na validação)
