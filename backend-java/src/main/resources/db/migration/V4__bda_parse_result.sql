CREATE TABLE bda_parse_result (
    id               UUID PRIMARY KEY,
    document_file_id UUID NOT NULL REFERENCES document_file(id),
    index_name       VARCHAR(128) NOT NULL,
    s3_output_path   VARCHAR(1024) NOT NULL,
    chunk_count      INTEGER NOT NULL DEFAULT 0,
    page_count       INTEGER NOT NULL DEFAULT 0,
    parser_type      VARCHAR(64),
    parser_version   VARCHAR(64),
    created_at       timestamp with time zone NOT NULL DEFAULT now()
);

CREATE INDEX ON bda_parse_result (document_file_id);
CREATE INDEX ON bda_parse_result (index_name);
