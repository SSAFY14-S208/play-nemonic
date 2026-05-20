ALTER TABLE gms_prompt_template
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN activated_at TIMESTAMP NULL,
    ADD COLUMN activated_by BIGINT NULL;

WITH latest_prompt AS (
    SELECT DISTINCT ON (feature_type)
           id
      FROM gms_prompt_template
     WHERE deleted_at IS NULL
     ORDER BY feature_type, updated_at DESC, id DESC
)
UPDATE gms_prompt_template prompt
   SET is_active = TRUE,
       activated_at = COALESCE(prompt.updated_at, prompt.created_at),
       activated_by = prompt.created_by
  FROM latest_prompt
 WHERE prompt.id = latest_prompt.id;

CREATE TABLE gms_prompt_feature_state (
    feature_type      prompt_feature_type NOT NULL,
    current_prompt_id BIGINT NULL,
    updated_by        BIGINT NULL,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP NOT NULL,
    CONSTRAINT PK_GMS_PROMPT_FEATURE_STATE PRIMARY KEY (feature_type),
    CONSTRAINT FK_gms_prompt_feature_state_prompt FOREIGN KEY (current_prompt_id) REFERENCES gms_prompt_template (id)
);

INSERT INTO gms_prompt_feature_state (
    feature_type,
    current_prompt_id,
    updated_by,
    created_at,
    updated_at
)
SELECT prompt.feature_type,
       prompt.id,
       prompt.activated_by,
       prompt.activated_at,
       prompt.activated_at
  FROM gms_prompt_template prompt
 WHERE prompt.deleted_at IS NULL
   AND prompt.is_active = TRUE;

CREATE UNIQUE INDEX IF NOT EXISTS uq_gms_prompt_active_feature
    ON gms_prompt_template (feature_type)
    WHERE deleted_at IS NULL
      AND is_active = TRUE;

CREATE INDEX IF NOT EXISTS idx_gms_prompt_active_status_created
    ON gms_prompt_template (is_active, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;
