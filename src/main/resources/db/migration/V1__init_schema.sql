CREATE TABLE users (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE posts (
    id UUID PRIMARY KEY,
    author_id UUID NOT NULL,
    content VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    parent_id UUID NULL,
    root_id UUID NOT NULL,
    reply_count INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_posts_author FOREIGN KEY (author_id) REFERENCES users (id),
    CONSTRAINT fk_posts_parent FOREIGN KEY (parent_id) REFERENCES posts (id),
    CONSTRAINT fk_posts_root FOREIGN KEY (root_id) REFERENCES posts (id),
    CONSTRAINT ck_posts_reply_count_non_negative CHECK (reply_count >= 0),
    CONSTRAINT ck_posts_content_len CHECK (char_length(content) <= 500)
);

CREATE TABLE edges (
    id UUID PRIMARY KEY,
    source_post_id UUID NOT NULL,
    target_post_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_edges_source_post FOREIGN KEY (source_post_id) REFERENCES posts (id),
    CONSTRAINT fk_edges_target_post FOREIGN KEY (target_post_id) REFERENCES posts (id),
    CONSTRAINT ck_edges_type_reply CHECK (type = 'REPLY'),
    CONSTRAINT ck_edges_no_self_loop CHECK (source_post_id <> target_post_id)
);

CREATE INDEX idx_posts_created_at_id_desc ON posts (created_at DESC, id DESC);
CREATE INDEX idx_posts_parent_id ON posts (parent_id);
CREATE INDEX idx_posts_root_id ON posts (root_id);
CREATE INDEX idx_edges_source_post_id ON edges (source_post_id);
CREATE INDEX idx_edges_target_post_id ON edges (target_post_id);
CREATE INDEX idx_users_email ON users (email);
