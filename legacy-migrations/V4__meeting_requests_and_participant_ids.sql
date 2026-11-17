-- Extend the original Phase 1 schema without rewriting already-applied migrations.
ALTER TABLE meetings DROP CONSTRAINT meetings_status_check;
ALTER TABLE meetings
    ADD CONSTRAINT ck_meetings_status CHECK (status IN
        ('REQUESTED', 'DRAFT', 'SEARCHING', 'SUGGESTED', 'CONFIRMED', 'CANCELLED', 'COMPLETED'));

-- Retain user_id as the participant's normalized user reference and preserve uniqueness.
ALTER TABLE meeting_participants ADD COLUMN id UUID;
UPDATE meeting_participants SET id = gen_random_uuid() WHERE id IS NULL;
ALTER TABLE meeting_participants ALTER COLUMN id SET NOT NULL;
ALTER TABLE meeting_participants DROP CONSTRAINT meeting_participants_pkey;
ALTER TABLE meeting_participants ADD CONSTRAINT meeting_participants_pkey PRIMARY KEY (id);
ALTER TABLE meeting_participants ADD CONSTRAINT uq_meeting_participants_meeting_user UNIQUE (meeting_id, user_id);
