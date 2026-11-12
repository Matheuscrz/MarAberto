-- V9: encerramento do plantão por inatividade.
--
-- O app não consegue avisar quando é fechado à força (nem quando o aparelho é
-- desligado, perde sinal ou o processo é morto pelo sistema). Sem isso, o
-- operador continuaria "de plantão" para sempre e entraria na seleção espacial
-- do SOS — recebendo chamados que não vai atender.
--
-- A solução é o plantão precisar de sinal de vida: o console renova a posição a
-- cada 5 minutos enquanto está aberto e visível. O job `ShiftIdleExpiry` encerra
-- o plantão cujo último sinal passou de `maraberto.dispatch.shift-idle-timeout`
-- (padrão 15 minutos), marcando `ended_reason = 'IDLE_TIMEOUT'` para o painel da
-- coordenação distinguir de encerramento manual.

ALTER TABLE shift_sessions
    ADD COLUMN last_seen_at TIMESTAMPTZ;

-- Backfill: o plantão existente vale a partir do último sinal conhecido.
UPDATE shift_sessions
SET last_seen_at = GREATEST(started_at, COALESCE(last_position_at, started_at))
WHERE last_seen_at IS NULL;

ALTER TABLE shift_sessions
    ALTER COLUMN last_seen_at SET DEFAULT NOW(),
    ALTER COLUMN last_seen_at SET NOT NULL;

-- Motivo do encerramento: MANUAL (operador encerrou), IDLE_TIMEOUT (sem sinal)
-- e LOGOUT (saiu da conta). Nulo enquanto o plantão está aberto.
ALTER TABLE shift_sessions
    ADD COLUMN ended_reason VARCHAR(16);

ALTER TABLE shift_sessions
    ADD CONSTRAINT ck_shift_end_reason CHECK (
        (ended_at IS NULL AND ended_reason IS NULL)
        OR (ended_at IS NOT NULL AND ended_reason IN ('MANUAL', 'IDLE_TIMEOUT', 'LOGOUT'))
    );

-- Fila do job: só os plantões abertos, ordenados pelo último sinal.
CREATE INDEX idx_shift_open_last_seen
    ON shift_sessions (last_seen_at)
    WHERE ended_at IS NULL;

COMMENT ON COLUMN shift_sessions.last_seen_at IS
    'Último sinal de vida do console do operador (abertura, posição ou leitura do plantão).';
COMMENT ON COLUMN shift_sessions.ended_reason IS
    'Motivo do encerramento: MANUAL, IDLE_TIMEOUT ou LOGOUT.';
