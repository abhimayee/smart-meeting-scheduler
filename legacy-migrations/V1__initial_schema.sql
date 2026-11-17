CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    time_zone VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_normalized CHECK (email = lower(trim(email)))
);

CREATE TABLE user_working_hours (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    CONSTRAINT ck_working_hours_order CHECK (start_time < end_time),
    CONSTRAINT uq_working_hours_user_day UNIQUE (user_id, day_of_week)
);
CREATE INDEX ix_working_hours_user ON user_working_hours(user_id);

CREATE TABLE meetings (
    id UUID PRIMARY KEY,
    title VARCHAR(300) NOT NULL,
    description TEXT,
    agenda JSONB NOT NULL DEFAULT '[]'::jsonb,
    duration_minutes INTEGER NOT NULL CHECK (duration_minutes > 0),
    organizer_id UUID NOT NULL REFERENCES users(id),
    preferred_start_date DATE,
    preferred_end_date DATE,
    preferred_start_time TIME,
    meeting_type VARCHAR(30) NOT NULL CHECK (meeting_type IN ('STANDARD', 'VIRTUAL', 'IN_PERSON')),
    status VARCHAR(30) NOT NULL CHECK (status IN ('DRAFT', 'SEARCHING', 'SUGGESTED', 'CONFIRMED', 'CANCELLED', 'COMPLETED')),
    scheduled_start TIMESTAMPTZ,
    scheduled_end TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_meeting_preferred_dates CHECK (preferred_start_date IS NULL OR preferred_end_date IS NULL OR preferred_end_date >= preferred_start_date),
    CONSTRAINT ck_meeting_scheduled_pair CHECK ((scheduled_start IS NULL) = (scheduled_end IS NULL)),
    CONSTRAINT ck_meeting_scheduled_order CHECK (scheduled_start IS NULL OR scheduled_start < scheduled_end)
);
CREATE INDEX ix_meetings_status ON meetings(status);
CREATE INDEX ix_meetings_scheduled_start ON meetings(scheduled_start) WHERE scheduled_start IS NOT NULL;

CREATE TABLE meeting_participants (
    meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    participant_type VARCHAR(20) NOT NULL CHECK (participant_type IN ('REQUIRED', 'OPTIONAL')),
    response_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (response_status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'TENTATIVE')),
    PRIMARY KEY (meeting_id, user_id)
);
CREATE INDEX ix_meeting_participants_user ON meeting_participants(user_id);

CREATE TABLE calendar_events (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(300),
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    busy BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_calendar_event_order CHECK (start_at < end_at)
);
CREATE INDEX ix_calendar_events_user_interval ON calendar_events(user_id, start_at, end_at);
