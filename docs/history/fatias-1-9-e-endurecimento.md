# Histórico de Entregas — Fatias 1 a 9 (Migrações V1–V20)

> Registro histórico das entregas concluídas até 2026-11-15. Para o estado operacional vigente, consulte `AGENTS.md`, `docs/Architecture.md` e `docs/Requirements.md`.

## Linha do Tempo das Migrações (`V1`–`V20`)

- `V1`: PostGIS e tabelas espaciais (`beaches`, `critical_zones`, `lifeguard_posts`)[cite: 1].
- `V2`: Zonas temporais e marés[cite: 1].
- `V3`: Chamados de emergência (`emergency_calls`, `emergency_dispatches`)[cite: 1].
- `V4`: Proteção de dados sensíveis (ADR-012)[cite: 1].
- `V5`: Transactional Outbox[cite: 1].
- `V6`/`V7`: Identidade embutida e sessões (`operational_users`, `auth_sessions`)[cite: 1].
- `V8`: Despacho por destinatário, `coverage_radius_m`, coluna geográfica gerada com GiST e `shift_sessions`[cite: 1].
- `V9`: Lease do plantão (`last_seen_at`)[cite: 1].
- `V10`: Escalonamento em anéis (5 a 20 km)[cite: 1].
- `V11`: Áreas de banho (`kind`), `beach_sectors` e `import_batches` (ADR-016, ADR-021)[cite: 1].
- `V12`: Arquivamento do KML aplicado no S3 (`source_storage_key`)[cite: 1].
- `V13`: Índice geográfico da projeção materializada `beach_map_features`[cite: 1].
- `V14`: Amostras anônimas de ocupação (`occupancy_samples`)[cite: 1].
- `V15`: Ciclo de vida append-only de sinalização (`flag_updates`) e estruturas (`deactivated_at`, `status_note`)[cite: 1].
- `V16`: Sinalização por GPS do aparelho e upload/normalização de mídia (`media_assets`, `flag_media`)[cite: 1].
- `V17`: Ocorrências abertas no aceite do SOS (`occurrences`, `occurrence_events`)[cite: 1].
- `V18`: Fila de áreas não identificadas (`unmapped_signals`, `unmapped_signal_media`)[cite: 1].
- `V19`: Fotos de postos e bases móveis (`lifeguard_post_media`, `mobile_base_media`)[cite: 1].
- `V20`: Encerramento de ocorrência, dados cifrados de vítima e fechamento transacional do SOS (`SosCallCloser`)[cite: 1].

## Resumo por Fatia Entregue

- **Fatia 1 (`1a795ea`, `1466485`, `cf58aa2`, `e08e3e2`)**: Importador KML/PostGIS (`KmlFeatureReader`, `BathingAreaImportService`), `V11` e `/gestao/importar`[cite: 1].
- **Fatia 2 (`e2012a7`, `6dd5097`, `30dccf6`, `403083a`, `7da31af`)**: Arquivamento S3/Garage (`V12`), busca pública por proximidade (`V13`) e backend de ocupação anônima (`V14`)[cite: 1].
- **Fatia 3 (`5afaa34`, `5c84378`, `8b255b5`)**: Endpoint `GET /api/v1/beaches/{id}/map` com `ETag`/`304`, contexto "Onde estou", ocupação por setor e desenho de polígonos no `MapView`[cite: 1].
- **Fatias 4, 5, 7 e 8 (`35b4bca`, `07d5aaf`, `2edfbcf`, `1ef3ece`)**: Ciclo de vida de bandeiras e estruturas (`V15`), sinalização por GPS e normalização JPEG sem EXIF (`V16`), fila de triagem para pontos fora do KML (`V18`) e gestão/fotos de estruturas em campo (`V19`)[cite: 1].
- **Fatia 6 (`35b4bca`–`1ef3ece`) e Fatia 9 (`3dd3b4c`, `80856c6`)**: Abertura idempotente de ocorrência no ack do SOS (`V17`), encerramento com desfecho, revelação auditada de vítima e fechamento do chamado na mesma transação (`V20`)[cite: 1].
