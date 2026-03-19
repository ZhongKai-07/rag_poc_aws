create index idx_document_file_filename on document_file (filename);
create index idx_ingestion_job_document_file_id on ingestion_job (document_file_id);
create index idx_ingestion_job_index_name on ingestion_job (index_name);
create index idx_question_history_index_name on question_history (index_name);
create index idx_question_history_index_name_asked_at on question_history (index_name, asked_at desc);
