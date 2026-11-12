-- V10: escalonamento do SOS por anéis de distância.
--
-- O despacho inicial (round 1) usa o raio de cobertura do posto/base, limitado
-- pelo teto da instalação. Quando ninguém assume, a escada abre anéis cada vez
-- maiores (5, 7, 10, 12, 15 e 20 km) até alguém aceitar; esgotada a escada, o
-- chamado vai para a coordenação (RN03). A decisão continua sendo do banco
-- (ADR-018): o job só pergunta "quem ainda não foi avisado e está dentro deste
-- anel?".

ALTER TABLE emergency_calls
    -- Quantos anéis já foram consumidos (0 = só o despacho inicial).
    ADD COLUMN escalation_step SMALLINT NOT NULL DEFAULT 0,
    -- Próximo horário em que a escada deve abrir o próximo anel; NULL quando
    -- não há mais o que ampliar (assumido, sem anéis ou escada esgotada).
    ADD COLUMN next_escalation_at TIMESTAMPTZ;

ALTER TABLE emergency_calls
    ADD CONSTRAINT ck_emergency_calls_escalation_step
    CHECK (escalation_step >= 0);

COMMENT ON COLUMN emergency_calls.escalation_step IS
    'Anéis de escalonamento já consumidos; 0 enquanto só o despacho inicial foi feito';
COMMENT ON COLUMN emergency_calls.next_escalation_at IS
    'Próxima abertura de anel; NULL encerra o escalonamento do chamado';

-- Fila do job: só chamados ainda abertos e com prazo de ampliação. Índice
-- parcial porque a maioria dos chamados termina no ack (RN: um atendente).
CREATE INDEX idx_emergency_calls_escalation
    ON emergency_calls (next_escalation_at)
    WHERE status = 'DISPATCHED' AND next_escalation_at IS NOT NULL;
