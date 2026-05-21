CREATE INDEX IF NOT EXISTS idx_gallery_active_user_artifact
    ON gallery (user_id, artifact_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_gallery_active_artifact_user
    ON gallery (artifact_id, user_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_artifact_source_room_created
    ON artifact (source_room_id, created_at ASC, id ASC)
    WHERE source_room_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_cs_inquiry_created_order
    ON cs_inquiry (created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_cs_inquiry_status_created
    ON cs_inquiry (status, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_cs_inquiry_type_created
    ON cs_inquiry (inquiry_type, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_cs_inquiry_user_created
    ON cs_inquiry (user_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_gms_prompt_active_created
    ON gms_prompt_template (created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_gms_prompt_active_feature_created
    ON gms_prompt_template (feature_type, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_gms_prompt_active_feature_updated
    ON gms_prompt_template (feature_type, updated_at DESC, id DESC)
    WHERE deleted_at IS NULL;
