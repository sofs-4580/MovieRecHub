-- Existing profiles are backfilled over HTTP from UserService; keep NULL during that transition.
ALTER TABLE profiles ADD COLUMN username VARCHAR(30);
ALTER TABLE profiles ADD CONSTRAINT ck_profiles_username_format
    CHECK (username ~ '^[a-z0-9_]{3,30}$');
CREATE UNIQUE INDEX uk_profiles_username ON profiles (username);
