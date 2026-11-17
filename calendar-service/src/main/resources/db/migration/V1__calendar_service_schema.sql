CREATE TABLE calendar_events (
    id UUID PRIMARY KEY,
    user_email VARCHAR(320) NOT NULL,
    title VARCHAR(300),
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    busy BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(20) NOT NULL DEFAULT 'BUSY' CHECK (status IN ('BUSY', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_calendar_event_order CHECK (start_at < end_at)
);
CREATE INDEX ix_calendar_events_user_interval ON calendar_events(user_email, start_at, end_at);
CREATE INDEX ix_calendar_events_busy_interval ON calendar_events(user_email, status, start_at, end_at);

-- Copy legacy calendar data into calendar ownership; legacy rows remain intact.
DO $$ BEGIN
    IF to_regclass('public.calendar_events') IS NOT NULL AND to_regclass('public.users') IS NOT NULL THEN
        INSERT INTO calendar_service.calendar_events
            (id, user_email, title, start_at, end_at, busy, status, created_at, updated_at)
        SELECT e.id, u.email, e.title, e.start_at, e.end_at, e.busy, e.status, e.created_at, e.updated_at
        FROM public.calendar_events e JOIN public.users u ON u.id = e.user_id
        ON CONFLICT (id) DO NOTHING;
    END IF;
END $$;
