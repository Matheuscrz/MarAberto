-- Publicação da outbox do SOS por WebSocket/STOMP.
--
-- A outbox guarda o evento cifrado em repouso; o publicador decifra, redige os
-- dados pessoais e entrega ao broker. `attempts` e `last_error` existem para
-- retry com limite e diagnóstico sem registrar PII.

ALTER TABLE emergency_outbox_events
    ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_error VARCHAR(200);

ALTER TABLE emergency_outbox_events
    ADD CONSTRAINT ck_emergency_outbox_attempts CHECK (attempts >= 0);

COMMENT ON COLUMN emergency_outbox_events.payload IS
    'JSON cifrado em repouso (AES-256-GCM, AAD maraberto:emergency-outbox:v1). Nunca gravar PII em claro.';
COMMENT ON COLUMN emergency_outbox_events.last_error IS
    'Somente o tipo da falha (ex.: IllegalStateException). Nunca incluir payload, coordenadas ou dados pessoais.';
COMMENT ON COLUMN emergency_outbox_events.attempts IS
    'Tentativas de publicação já realizadas; ao atingir o limite o evento vira FAILED.';
