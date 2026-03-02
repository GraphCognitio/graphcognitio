CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS embedding vector(192),
    ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(120),
    ADD COLUMN IF NOT EXISTS embedding_updated_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_posts_embedding_ivfflat
    ON posts
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100)
    WHERE embedding IS NOT NULL;
