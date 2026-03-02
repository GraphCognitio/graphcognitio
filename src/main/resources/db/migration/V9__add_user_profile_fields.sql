ALTER TABLE users
ADD COLUMN username VARCHAR(50);

-- Provide a fallback username for existing users
UPDATE users
SET username = LOWER(REGEXP_REPLACE(name, '[^a-zA-Z0-9]+', '', 'g')) || '_' || SUBSTR(id::text, 1, 8)
WHERE username IS NULL;

-- Now make it NOT NULL and UNIQUE
ALTER TABLE users
ALTER COLUMN username SET NOT NULL,
ADD CONSTRAINT uk_users_username UNIQUE (username);

ALTER TABLE users
ADD COLUMN bio VARCHAR(160),
ADD COLUMN avatar_url VARCHAR(2048);
