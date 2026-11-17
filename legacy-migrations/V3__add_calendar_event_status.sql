ALTER TABLE calendar_events
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'BUSY';

UPDATE calendar_events
SET status = CASE WHEN busy THEN 'BUSY' ELSE 'CANCELLED' END;

ALTER TABLE calendar_events
    ADD CONSTRAINT ck_calendar_event_status CHECK (status IN ('BUSY', 'CANCELLED'));

CREATE INDEX ix_calendar_events_user_status_interval
    ON calendar_events(user_id, status, start_at, end_at);
