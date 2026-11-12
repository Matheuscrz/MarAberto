-- Arquivo original da importação no armazenamento de objetos (ADR-016).
--
-- O KML aplicado fica no Garage endereçado pelo SHA-256 do conteúdo
-- (`bathing-areas/<UF>/<sha256>.kml`), para auditoria e reprocessamento. A
-- coluna é anulável de propósito: se o storage estiver indisponível a
-- importação do mapa continua valendo e o lote registra que o arquivo não foi
-- arquivado (`NULL`).

ALTER TABLE import_batches
    ADD COLUMN source_storage_key VARCHAR(512);

COMMENT ON COLUMN import_batches.source_storage_key IS
    'Chave do KML no storage S3-compatível; NULL quando o arquivo não foi arquivado';
