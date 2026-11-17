ALTER TABLE user_working_hours DROP CONSTRAINT uq_working_hours_user_day;
ALTER TABLE user_working_hours
    ADD CONSTRAINT uq_working_hours_user_interval
    UNIQUE (user_id, day_of_week, start_time, end_time);
