CREATE TABLE profiles (
    user_id BIGINT PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL DEFAULT '',
    bio VARCHAR(2000) NOT NULL DEFAULT '',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_profiles_user_id_positive CHECK (user_id > 0)
);

CREATE TABLE profile_favorite_genres (
    user_id BIGINT NOT NULL,
    genre VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, genre),
    CONSTRAINT fk_profile_favorite_genres_profile
        FOREIGN KEY (user_id) REFERENCES profiles (user_id) ON DELETE CASCADE,
    CONSTRAINT ck_profile_favorite_genres_genre CHECK (
        genre IN ('ACTION', 'COMEDY', 'DRAMA', 'HORROR', 'ROMANCE',
                  'SCI_FI', 'THRILLER', 'ANIMATION', 'DOCUMENTARY', 'FANTASY')
    )
);
