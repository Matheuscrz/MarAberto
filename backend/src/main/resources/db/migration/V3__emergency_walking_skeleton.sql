CREATE TABLE emergency_calls (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_request_id VARCHAR(100) NOT NULL,
    latitude          DOUBLE PRECISION NOT NULL,
    longitude         DOUBLE PRECISION NOT NULL,
    accuracy_meters   DOUBLE PRECISION,
    status            VARCHAR(30) NOT NULL DEFAULT 'DISPATCHED',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_emergency_calls_client_request_id UNIQUE (client_request_id),
    CONSTRAINT ck_emergency_calls_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_emergency_calls_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_emergency_calls_accuracy CHECK (accuracy_meters IS NULL OR accuracy_meters > 0),
    CONSTRAINT ck_emergency_calls_status CHECK (
        status IN ('DISPATCHED', 'ACKNOWLEDGED', 'EN_ROUTE', 'ON_SCENE', 'RESOLVED', 'CANCELLED', 'FALSE_ALARM')
    )
);

CREATE INDEX idx_emergency_calls_open
    ON emergency_calls (created_at)
    WHERE status IN ('DISPATCHED', 'ACKNOWLEDGED', 'EN_ROUTE', 'ON_SCENE');

CREATE TABLE emergency_dispatches (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    emergency_call_id  UUID NOT NULL REFERENCES emergency_calls (id) ON DELETE CASCADE,
    assigned_post_id   UUID,
    status             VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_emergency_dispatches_status CHECK (
        status IN ('PENDING', 'NOTIFIED', 'ACKNOWLEDGED', 'CANCELLED')
    )
);

CREATE INDEX idx_emergency_dispatches_call ON emergency_dispatches (emergency_call_id);

CREATE TABLE emergency_outbox_events (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id       UUID NOT NULL,
    event_type         VARCHAR(120) NOT NULL,
    payload            JSONB NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    occurred_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at       TIMESTAMPTZ,
    CONSTRAINT ck_emergency_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX idx_emergency_outbox_pending
    ON emergency_outbox_events (occurred_at)
    WHERE status = 'PENDING';
