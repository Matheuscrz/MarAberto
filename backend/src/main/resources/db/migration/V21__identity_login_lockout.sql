-- Proteção contra força bruta no login único (RN de segurança, ADR-009).
--
-- O contador é por conta e nunca revela o estado: a resposta continua sendo
-- "credenciais inválidas", e o bloqueio só impede que a senha correta seja
-- aceita durante a janela. `locked_until` no passado é equivalente a não
-- bloqueado, então não há job de limpeza.
ALTER TABLE operational_users
    ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER NOT NULL DEFAULT 0;

ALTER TABLE operational_users
    ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP WITH TIME ZONE;

-- A consulta de login é por índice cego de e-mail/CPF/matrícula; o bloqueio é
-- lido junto com a linha do usuário. Nenhum índice adicional é necessário.
