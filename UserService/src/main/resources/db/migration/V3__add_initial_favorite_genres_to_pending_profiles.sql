ALTER TABLE pending_profiles
    ADD COLUMN favorite_genres JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD CONSTRAINT ck_pending_profiles_favorite_genres_array
        CHECK (jsonb_typeof(favorite_genres) = 'array' AND jsonb_array_length(favorite_genres) <= 10);
