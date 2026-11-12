-- Indicador de ocupação anônimo e aproximado (RF10, RN06, RNF04).
--
-- O aparelho informa a posição atual; o servidor resolve o SETOR que a contém
-- na hora e grava apenas três coisas: o setor, a janela de 15 minutos e o HMAC
-- de um token efêmero do cliente. A coordenada **nunca** é persistida, não há
-- histórico por aparelho e não existe identificador de dispositivo: o HMAC
-- muda quando o app recarrega e o mesmo token só conta uma vez por setor por
-- janela.
--
-- O contador alimenta a estimativa por janela móvel de 15 min; abaixo da
-- amostra mínima (RN06) a API responde "dados insuficientes" em vez do nível.
-- As linhas velhas são apagadas na própria escrita (retenção curta), então a
-- tabela não guarda trilha nenhuma.

CREATE TABLE occupancy_samples (
    id              BIGSERIAL PRIMARY KEY,
    beach_id        UUID NOT NULL REFERENCES beaches (id) ON DELETE CASCADE,
    sector_id       UUID NOT NULL REFERENCES beach_sectors (id) ON DELETE CASCADE,
    anonymous_token VARCHAR(64) NOT NULL,
    bucket_start    TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE occupancy_samples IS
    'Amostras anônimas de presença por setor; sem coordenada, sem IP e sem histórico por aparelho';

-- Um token conta uma vez por setor por janela: recarregar a tela não infla.
CREATE UNIQUE INDEX uq_occupancy_token_sector_bucket
    ON occupancy_samples (anonymous_token, sector_id, bucket_start);

CREATE INDEX idx_occupancy_beach_window
    ON occupancy_samples (beach_id, created_at DESC);

CREATE INDEX idx_occupancy_created
    ON occupancy_samples (created_at);
