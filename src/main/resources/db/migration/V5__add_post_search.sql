ALTER TABLE posts
    ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        to_tsvector('simple', COALESCE(content, ''))
    ) STORED;

CREATE INDEX idx_posts_search_vector
    ON posts
    USING GIN (search_vector);
