-- V23: alerta da sinalização fora do mapeamento e plantão sem estrutura.
--
-- 1. A sinalização lançada fora do mapeamento continua indo para a triagem,
--    mas passa a valer no mapa (público e da coordenação) como alerta de área
--    não mapeada. A validade é a mesma da bandeira: o alerta nasce com o
--    lançamento e sai sozinho quando vence, sem depender de decisão humana.
-- 2. O plantão deixa de exigir posto ou base: o guarda-vidas é um ponto no
--    mapa e a estrutura é outro. Posto fixo/base móvel continuam opcionais.
--    A base móvel ganha ciclo próprio (não é mais desativada ao encerrar o
--    turno).

ALTER TABLE unmapped_signals
    ADD COLUMN IF NOT EXISTS valid_until timestamptz;

-- Registros anteriores ao alerta: mesma regra que passará a valer no insert
-- (12 h a partir do lançamento). O que já venceu continua na fila de triagem,
-- apenas não é mais desenhado no mapa.
UPDATE unmapped_signals
   SET valid_until = created_at + INTERVAL '12 hours'
 WHERE valid_until IS NULL;

ALTER TABLE unmapped_signals
    ALTER COLUMN valid_until SET DEFAULT (now() + INTERVAL '12 hours'),
    ALTER COLUMN valid_until SET NOT NULL;

-- Leitura pública do alerta: só PENDING dentro da validade.
CREATE INDEX IF NOT EXISTS idx_unmapped_signals_alert
    ON unmapped_signals (valid_until DESC, id)
    WHERE status = 'PENDING';

-- Plantão sem estrutura: no máximo um destino, podendo ser nenhum.
ALTER TABLE shift_sessions
    DROP CONSTRAINT ck_shift_target;

ALTER TABLE shift_sessions
    ADD CONSTRAINT ck_shift_target
        CHECK (num_nonnulls(post_id, mobile_base_id) <= 1);
