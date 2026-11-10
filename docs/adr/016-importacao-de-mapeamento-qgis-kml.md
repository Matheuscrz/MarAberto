# ADR-016: Importação de mapeamento QGIS/KML com setores de praia

- Status: Aceito (2026-11-10)
- Contexto: a instalação recebe o mapeamento de praias e setores feito no QGIS e entregue em KML/KMZ. O KML é bom para interoperabilidade e para abrir no Google Earth, mas é XML verboso, aceita geometria 3D, estilos e pastas fora do padrão e não tem um contrato de dados estável. Ler KML dentro do backend adicionaria parsers pesados (GeoTools/XML) ao runtime, com risco de segurança e de comportamento divergente entre arquivos. Ao mesmo tempo, a operação precisa de uma unidade menor que a praia — o **setor** — para plantão, bandeira, ocorrência, sinalização e ocupação.
- Decisão:
  - **QGIS é a ferramenta de autoria** (EPSG:4326, geometria validada antes de exportar). **KML/KMZ é formato de troca**, não formato de produção.
  - **GeoJSON (RFC 7946) é o formato canônico de importação** e do Region Pack. A conversão KML/KMZ → GeoJSON é feita **offline** com `ogr2ogr` (GDAL) ou exportação direta do QGIS, fora do runtime da aplicação.
  - O importador é **idempotente por `external_id`** (upsert por camada), versionado e auditado; roda por comando/etapa de implantação, nunca por SQL manual em produção.
  - O banco guarda a geometria completa em PostGIS; a simplificação (`ST_SimplifyPreserveTopology`) existe só na cópia enviada ao cliente.
  - Criar a tabela **`beach_sectors`** (setor/trecho operacional dentro da praia). O setor é opcional para o banhista, mas é a unidade usada pela operação. A soma dos setores não precisa cobrir 100 % da praia; feições fora de praia são rejeitadas com aviso.
  - Guardar **proveniência** em cada camada importada: `source` (QGIS/KML/município/Marinha), `source_version`, `imported_at`, `imported_by` e o arquivo de origem.
- Contrato mínimo das feições:

| Camada          | Geometria                 | Campos obrigatórios                                  | Campos opcionais                                      |
| --------------- | ------------------------- | ---------------------------------------------------- | ----------------------------------------------------- |
| `beaches`       | `Polygon`/`MultiPolygon`  | `external_id`, `name`                                | `municipality`, `state_code`, `source`                |
| `beach_sectors` | `Polygon`/`MultiPolygon`  | `external_id`, `beach_external_id`, `name`           | `kind` (`NORTH`/`SOUTH`/`CENTRAL`/`OTHER`), `order`   |
| `lifeguard_posts` | `Point`                 | `external_id`, `name`, `type` (`FIXED`/`MOBILE`)     | `beach_external_id`, `sector_external_id`, `resources` |
| `critical_zones` | `Polygon`/`MultiPolygon` | `external_id`, `name`, `risk_type`, `persistence`    | `valid_from`, `valid_until`, `source`                 |

- Pipeline:
  1. **Autoria no QGIS** em EPSG:4326, camadas nomeadas como acima, com `external_id` estável (nunca reutilizar id de outra feição).
  2. **Exportação** em KML/KMZ ou GeoJSON. Para KML: `ogr2ogr -f GeoJSON -t_srs EPSG:4326 saida.geojson entrada.kml`.
  3. **Validação offline** (script/CLI do importador): esquema, campos obrigatórios, `external_id` único, geometria não vazia, `ST_IsValid`/`ST_MakeValid`, força 2D, reprojeção para 4326, praia existente para cada setor e sobreposição suspeita entre praias.
  4. **Importação transacional** por camada, com relatório de aceitos/rejeitados e `dry-run` obrigatório antes de aplicar.
  5. **Publicação** de `BeachChanged`/`SectorChanged` para invalidar cache e a projeção de busca.
- Alternativas:
  - **Ler KML no backend em produção** (rejeitada): adiciona parser XML/GeoTools ao runtime, amplia superfície de ataque e não resolve a falta de contrato de dados.
  - **Só GeoJSON, sem KML** (rejeitada na entrada): o órgão já produz KML no QGIS; exigir GeoJSON direto aumentaria o atrito de adoção. A conversão fica no pipeline, não no produto.
  - **Setor como atributo textual da praia** (rejeitada): impede plantão/ocorrência/bandeira por trecho e não aproveita o mapeamento do QGIS.
  - **Shapefile** (rejeitada): codificação e nomes de campo problemáticos; GeoJSON é mais simples e legível.
- Consequências:
  - O backend não ganha dependência de GDAL/GeoTools; a conversão é uma etapa de implantação documentada.
  - O mapeamento pode ser reimportado sem duplicar praias/setores; correções no QGIS viram nova versão auditada.
  - `beach_sectors` passa a ser referenciado por `operations` (plantão/bandeira), `occurrences` (`beach_sector_id`), `safety` (sinalização) e `occupancy`; o escopo de autorização pode ser por setor.
  - Quem importar precisa de um `external_id` estável; o importador avisa quando uma feição existente some do arquivo (não apaga automaticamente).
  - A delimitação oficial (Marinha/DHN ou município) continua sendo a referência; o mapeamento do órgão é operacional e não substitui a oficial.
  - Privacidade: geometria de praia/setor é pública; posição de operador e coordenada de ocorrência continuam sensíveis e **não** entram no GeoJSON público.
