CREATE TABLE meetings (
    id UUID PRIMARY KEY,
    title VARCHAR(300) NOT NULL,
    agenda JSONB NOT NULL,
    duration_minutes INTEGER NOT NULL CHECK (duration_minutes > 0),
    organizer_email VARCHAR(320) NOT NULL,
    meeting_type VARCHAR(30) NOT NULL CHECK (meeting_type IN ('STANDARD', 'VIRTUAL', 'IN_PERSON')),
    status VARCHAR(30) NOT NULL CHECK (status IN ('REQUESTED', 'DRAFT', 'SEARCHING', 'SUGGESTED', 'CONFIRMED', 'CANCELLED', 'COMPLETED')),
    scheduled_start TIMESTAMPTZ,
    scheduled_end TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_meeting_scheduled_pair CHECK ((scheduled_start IS NULL) = (scheduled_end IS NULL)),
    CONSTRAINT ck_meeting_scheduled_order CHECK (scheduled_start IS NULL OR scheduled_start < scheduled_end)
);
CREATE INDEX ix_meetings_status ON meetings(status);
CREATE INDEX ix_meetings_scheduled_start ON meetings(scheduled_start) WHERE scheduled_start IS NOT NULL;
CREATE TABLE meeting_participants (
    id UUID PRIMARY KEY,
    meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    email VARCHAR(320) NOT NULL,
    participant_type VARCHAR(20) NOT NULL CHECK (participant_type IN ('REQUIRED', 'OPTIONAL')),
    response_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (response_status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'TENTATIVE')),
    CONSTRAINT uq_meeting_participant_email UNIQUE (meeting_id, email)
);
CREATE INDEX ix_meeting_participants_email ON meeting_participants(email);

-- Copy supported legacy lifecycle states by email; retain the original public tables.
DO $$ BEGIN
    IF to_regclass('public.meetings') IS NOT NULL AND to_regclass('public.users') IS NOT NULL THEN
        INSERT INTO meeting_service.meetings
            (id, title, agenda, duration_minutes, organizer_email, meeting_type, status,
             scheduled_start, scheduled_end, created_at, updated_at)
        SELECT m.id, m.title, m.agenda, m.duration_minutes, u.email, m.meeting_type, m.status,
               m.scheduled_start, m.scheduled_end, m.created_at, m.updated_at
        FROM public.meetings m JOIN public.users u ON u.id = m.organizer_id
        ON CONFLICT (id) DO NOTHING;
    END IF;
    IF to_regclass('public.meeting_participants') IS NOT NULL AND to_regclass('public.users') IS NOT NULL THEN
        INSERT INTO meeting_service.meeting_participants
            (id, meeting_id, email, participant_type, response_status)
        SELECT p.id, p.meeting_id, u.email, p.participant_type, p.response_status
        FROM public.meeting_participants p JOIN public.users u ON u.id = p.user_id
        JOIN public.meetings m ON m.id = p.meeting_id
        ON CONFLICT (id) DO NOTHING;
    END IF;
END $$;
