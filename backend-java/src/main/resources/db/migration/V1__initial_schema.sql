create table document_file (
    id uuid primary key,
    filename varchar(512) not null,
    index_name varchar(128) not null unique,
    storage_path varchar(1024) not null,
    status varchar(64) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table ingestion_job (
    id uuid primary key,
    document_file_id uuid not null,
    index_name varchar(128) not null,
    status varchar(64) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_ingestion_job_document_file
        foreign key (document_file_id) references document_file (id)
);

create table question_history (
    id uuid primary key,
    index_name varchar(128) not null,
    question varchar(2000) not null,
    asked_at timestamp with time zone not null
);
