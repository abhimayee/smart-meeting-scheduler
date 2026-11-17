CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL CONSTRAINT uq_users_email UNIQUE,
    display_name VARCHAR(200) NOT NULL,
    time_zone VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_users_email_normalized CHECK (email = lower(trim(email)))
);
CREATE TABLE user_working_hours (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    CONSTRAINT ck_working_hours_order CHECK (start_time < end_time)
);
CREATE INDEX ix_working_hours_user ON user_working_hours(user_id);
CREATE INDEX ix_working_hours_user_day ON user_working_hours(user_id, day_of_week, start_time);

-- Copy legacy public-schema data once, leaving the original tables untouched.
DO $$ BEGIN
    IF to_regclass('public.users') IS NOT NULL THEN
        INSERT INTO user_service.users (id, email, display_name, time_zone, created_at, updated_at)
        SELECT id, email, display_name, time_zone, created_at, updated_at FROM public.users
        ON CONFLICT (email) DO NOTHING;
    END IF;
    IF to_regclass('public.user_working_hours') IS NOT NULL THEN
        INSERT INTO user_service.user_working_hours (id, user_id, day_of_week, start_time, end_time)
        SELECT id, user_id, day_of_week, start_time, end_time FROM public.user_working_hours
        ON CONFLICT (id) DO NOTHING;
    END IF;
END $$;
