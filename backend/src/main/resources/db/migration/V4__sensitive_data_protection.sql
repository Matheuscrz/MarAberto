-- Proteção de dados sensíveis em repouso.
--
-- O payload da outbox carrega localização precisa do SOS. Ele passa a ser
-- armazenado cifrado com AES-256-GCM (formato enc:v1:<keyId>:<base64url>),
-- decifrado apenas pelo publicador da outbox antes de desserializar o evento.
ALTER TABLE emergency_outbox_events
    ALTER COLUMN payload TYPE TEXT USING payload::text;

COMMENT ON COLUMN emergency_outbox_events.payload IS
    'Payload JSON cifrado em repouso (AES-256-GCM, prefixo enc:v1:). Não inserir PII em claro.';

COMMENT ON COLUMN emergency_calls.latitude IS
    'Dado pessoal sensível (localização precisa). Não registrar em logs; acesso restrito.';
COMMENT ON COLUMN emergency_calls.longitude IS
    'Dado pessoal sensível (localização precisa). Não registrar em logs; acesso restrito.';
