CREATE TABLE profile_favorite_decades (
    user_id BIGINT NOT NULL,
    decade INTEGER NOT NULL,
    PRIMARY KEY (user_id, decade),
    CONSTRAINT fk_profile_favorite_decades_profile
        FOREIGN KEY (user_id) REFERENCES profiles (user_id) ON DELETE CASCADE,
    CONSTRAINT ck_profile_favorite_decades_valid
        CHECK (decade >= 1880 AND decade <= 2090 AND decade % 10 = 0)
);
