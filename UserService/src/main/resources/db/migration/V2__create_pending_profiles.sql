CREATE TABLE pending_profiles (
    user_id BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempts BIGINT NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_error VARCHAR(80)
);

CREATE INDEX ix_pending_profiles_due ON pending_profiles (next_attempt_at, user_id);
