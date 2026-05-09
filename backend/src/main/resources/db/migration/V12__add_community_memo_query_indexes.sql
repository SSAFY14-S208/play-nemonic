CREATE INDEX IF NOT EXISTS idx_cm_visible_wall_order
    ON community_memo (z_index ASC, attached_at ASC, id ASC)
    WHERE deleted_at IS NULL AND is_hidden = FALSE;

CREATE INDEX IF NOT EXISTS idx_cm_visible_fifo_order
    ON community_memo (attached_at ASC, id ASC)
    WHERE deleted_at IS NULL AND is_hidden = FALSE;

CREATE INDEX IF NOT EXISTS idx_cm_admin_updated_order
    ON community_memo (updated_at DESC, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_cm_admin_hidden_updated
    ON community_memo (is_hidden, updated_at DESC, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_cm_admin_moderation_updated
    ON community_memo (moderation_status, updated_at DESC, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_cm_admin_reported_updated
    ON community_memo (updated_at DESC, created_at DESC, id DESC)
    WHERE deleted_at IS NULL AND report_count > 0;

CREATE INDEX IF NOT EXISTS idx_cm_admin_unreported_updated
    ON community_memo (updated_at DESC, created_at DESC, id DESC)
    WHERE deleted_at IS NULL AND report_count = 0;

CREATE INDEX IF NOT EXISTS idx_cm_admin_direct_updated
    ON community_memo (updated_at DESC, created_at DESC, id DESC)
    WHERE deleted_at IS NULL AND artifact_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_cm_admin_gallery_updated
    ON community_memo (updated_at DESC, created_at DESC, id DESC)
    WHERE deleted_at IS NULL AND artifact_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_cmr_memo_created_order
    ON community_memo_report (memo_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_cmr_memo_reason_created
    ON community_memo_report (memo_id, reason, created_at DESC, id DESC);
