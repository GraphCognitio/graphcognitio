ALTER TABLE edges
    DROP CONSTRAINT IF EXISTS ck_edges_type_reply;

ALTER TABLE edges
    ADD CONSTRAINT ck_edges_type_supported CHECK (type IN ('REPLY', 'RELATED'));
