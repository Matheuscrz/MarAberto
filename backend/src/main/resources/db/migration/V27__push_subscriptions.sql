-- Inscrições de Web Push (VAPID) e tokens FCM (Capacitor Android) dos operacionais.
CREATE TABLE IF NOT EXISTS push_subscriptions (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES operational_users(id) ON DELETE CASCADE,
    channel    VARCHAR(16) NOT NULL CHECK (channel IN ('WEB_PUSH', 'FCM')),
    endpoint   TEXT NOT NULL UNIQUE,
    p256dh     TEXT,
    auth       TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_push_subscriptions_user
    ON push_subscriptions (user_id);
