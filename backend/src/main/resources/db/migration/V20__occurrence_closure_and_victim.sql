-- V20: encerramento e edição da ocorrência (ADR-013 emendada).
--
-- A ocorrência nasce no aceite do SOS e precisa sair da lista de atendimentos
-- em curso quando o guarda-vidas termina o serviço de campo: o chamado do SOS
-- é encerrado no mesmo passo e a ficha fica disponível para a coordenação
-- completar os dados (vítima identificada depois, protocolo do CIOSP etc.).
--
-- `responder_id` é quem assumiu o chamado: a visibilidade do guarda-vidas
-- passa a ser "o que eu atendi", não "o que é do meu posto". `closed_by`
-- preserva quem encerrou. Nada é apagado: a ficha encerrada continua no
-- histórico (RN11).

ALTER TABLE occurrences
    ADD COLUMN responder_id UUID REFERENCES operational_users (id),
    ADD COLUMN closed_by    UUID REFERENCES operational_users (id),
    -- Dados de vítima são opcionais, cifrados pela aplicação (ADR-012) e
    -- mascarados por padrão; a revelação é auditada em occurrence_events.
    ADD COLUMN victim_name      TEXT,
    ADD COLUMN victim_age       TEXT,
    ADD COLUMN victim_contact   TEXT,
    ADD COLUMN victim_condition TEXT;

-- O chamado assumido já sabe quem deu o ack: a ocorrência herda esse vínculo.
UPDATE occurrences o
SET responder_id = d.recipient_user_id
FROM emergency_dispatches d
WHERE d.emergency_call_id = o.emergency_call_id
  AND d.status = 'ACKNOWLEDGED'
  AND o.responder_id IS NULL;

CREATE INDEX idx_occ_responder ON occurrences (responder_id, occurred_at DESC);

-- O desfecho do atendimento pode ser "encaminhado ao serviço de saúde", que
-- internamente encerra a ocorrência como atendida.
ALTER TABLE occurrences DROP CONSTRAINT chk_occ_outcome;
ALTER TABLE occurrences ADD CONSTRAINT chk_occ_outcome CHECK (
    outcome_type IS NULL
    OR outcome_type IN ('RESOLVED', 'REFERRED', 'FALSE_ALARM', 'CANCELLED'));

-- Encerrar a ocorrência encerra o chamado do SOS: sem isso ele continuaria em
-- "Em atendimento por você" no console para sempre.
ALTER TABLE emergency_calls
    ADD COLUMN resolved_at TIMESTAMPTZ,
    ADD COLUMN resolved_by UUID REFERENCES operational_users (id);
