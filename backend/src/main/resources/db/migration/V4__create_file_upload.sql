CREATE TABLE file_upload (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    byte_size BIGINT NOT NULL,
    object_key TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT PK_FILE_UPLOAD PRIMARY KEY (id),
    CONSTRAINT UQ_FILE_UPLOAD_OBJECT_KEY UNIQUE (object_key),
    CONSTRAINT FK_FILE_UPLOAD_USER FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT CK_FILE_UPLOAD_PURPOSE CHECK (
        purpose IN (
            'COMMUNITY',
            'RELAY_DRAWING',
            'FLIPBOOK',
            'FLIPBOOK_GIF',
            'FORTUNE',
            'INFINITE_CANVAS',
            'PHONE'
        )
    ),
    CONSTRAINT CK_FILE_UPLOAD_STATUS CHECK (
        status IN ('PENDING', 'UPLOADED', 'DELETED')
    )
);
