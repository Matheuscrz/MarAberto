CREATE TABLE IF NOT EXISTS operational_users (
    id UUID PRIMARY KEY,
    name VARCHAR(512) NOT NULL,
    email VARCHAR(512) NOT NULL,
    email_bidx VARCHAR(64) NOT NULL UNIQUE,
    cpf VARCHAR(512) NOT NULL,
    cpf_bidx VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_operational_users_email_bidx ON operational_users (email_bidx);
CREATE INDEX IF NOT EXISTS idx_operational_users_cpf_bidx ON operational_users (cpf_bidx);