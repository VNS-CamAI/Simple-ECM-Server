CREATE TABLE FILE_ECM (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name VARCHAR(1000) NOT NULL,
    file_path VARCHAR(2000) NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    file_size BIGINT,
    category VARCHAR(255) NOT NULL,
    version INTEGER,
    date_upload TIMESTAMP,
    update_at TIMESTAMP
);
