-- Keep V1 immutable. This migration moves databases created by the original V1
-- to the current initial-schema shape used by the application.

-- Drop foreign keys that either need UUID column conversion first or are no
-- longer part of the current baseline schema.
ALTER TABLE infinite_canvas_artifact DROP CONSTRAINT IF EXISTS fk_artifact_to_infinite_canvas_artifact;
ALTER TABLE flipbook_artifact DROP CONSTRAINT IF EXISTS fk_artifact_to_flipbook_artifact;
ALTER TABLE relay_drawing_artifact DROP CONSTRAINT IF EXISTS fk_artifact_to_relay_drawing_artifact;
ALTER TABLE phone_artifact DROP CONSTRAINT IF EXISTS fk_artifact_to_phone_artifact;
ALTER TABLE fortune_artifact DROP CONSTRAINT IF EXISTS fk_artifact_to_fortune_artifact;
ALTER TABLE fortune_artifact_asset DROP CONSTRAINT IF EXISTS fk_fortune_artifact_asset_asset;
ALTER TABLE fortune_artifact_asset DROP CONSTRAINT IF EXISTS fk_fortune_artifact_asset_artifact;
ALTER TABLE gallery DROP CONSTRAINT IF EXISTS fk_gallery_user;
ALTER TABLE gallery DROP CONSTRAINT IF EXISTS fk_gallery_artifact;
ALTER TABLE community_memo DROP CONSTRAINT IF EXISTS fk_community_memo_user;
ALTER TABLE community_memo DROP CONSTRAINT IF EXISTS fk_community_memo_artifact;
ALTER TABLE community_memo DROP CONSTRAINT IF EXISTS fk_community_memo_reviewer;
ALTER TABLE community_memo_report DROP CONSTRAINT IF EXISTS fk_memo_report_memo;
ALTER TABLE community_memo_report DROP CONSTRAINT IF EXISTS fk_memo_report_user;
ALTER TABLE cs_inquiry DROP CONSTRAINT IF EXISTS fk_cs_inquiry_user;
ALTER TABLE cs_inquiry DROP CONSTRAINT IF EXISTS fk_cs_inquiry_admin;
ALTER TABLE backoffice_setting DROP CONSTRAINT IF EXISTS fk_backoffice_setting_admin;
ALTER TABLE gms_prompt_template DROP CONSTRAINT IF EXISTS fk_gms_prompt_template_admin;

-- Rename enum types to the current naming convention.
ALTER TYPE asset_role_enum RENAME TO asset_role_type;
ALTER TYPE feature_type_enum RENAME TO prompt_feature_type;
ALTER TYPE report_reason_enum RENAME TO report_reason_type;
ALTER TYPE inquiry_type_enum RENAME TO inquiry_type_type;
ALTER TYPE inquiry_status_enum RENAME TO inquiry_status_type;
ALTER TYPE hidden_reason_enum RENAME TO hidden_reason_type;
ALTER TYPE moderation_status_enum RENAME TO moderation_status_type;
ALTER TYPE artifact_kind_enum RENAME TO artifact_kind_type;
ALTER TYPE admin_role_enum RENAME TO admin_role_type;
ALTER TYPE deleted_reason_enum RENAME TO deleted_reason_type;

-- Convert UUID-shaped string identifiers to PostgreSQL UUID columns.
ALTER TABLE app_user ALTER COLUMN id TYPE UUID USING id::uuid;
ALTER TABLE artifact ALTER COLUMN id TYPE UUID USING id::uuid;

ALTER TABLE infinite_canvas_artifact ALTER COLUMN artifact_id TYPE UUID USING artifact_id::uuid;
ALTER TABLE flipbook_artifact ALTER COLUMN artifact_id TYPE UUID USING artifact_id::uuid;
ALTER TABLE relay_drawing_artifact ALTER COLUMN artifact_id TYPE UUID USING artifact_id::uuid;
ALTER TABLE phone_artifact ALTER COLUMN artifact_id TYPE UUID USING artifact_id::uuid;
ALTER TABLE fortune_artifact ALTER COLUMN artifact_id TYPE UUID USING artifact_id::uuid;
ALTER TABLE fortune_artifact_asset ALTER COLUMN artifact_id TYPE UUID USING artifact_id::uuid;

ALTER TABLE gallery ALTER COLUMN id TYPE UUID USING id::uuid;
ALTER TABLE gallery ALTER COLUMN user_id TYPE UUID USING user_id::uuid;
ALTER TABLE gallery ALTER COLUMN artifact_id TYPE UUID USING artifact_id::uuid;

ALTER TABLE community_memo ALTER COLUMN id TYPE UUID USING id::uuid;
ALTER TABLE community_memo ALTER COLUMN user_id TYPE UUID USING user_id::uuid;
ALTER TABLE community_memo ALTER COLUMN artifact_id TYPE UUID USING artifact_id::uuid;

ALTER TABLE community_memo_report ALTER COLUMN memo_id TYPE UUID USING memo_id::uuid;
ALTER TABLE community_memo_report ALTER COLUMN user_id TYPE UUID USING user_id::uuid;

ALTER TABLE cs_inquiry ALTER COLUMN user_id TYPE UUID USING user_id::uuid;

-- Apply column-level schema changes added after the original V1.
ALTER TABLE infinite_canvas_artifact DROP COLUMN aspect_ratio;

ALTER TABLE fortune_artifact ADD COLUMN fortune_image_url VARCHAR(200) NOT NULL DEFAULT '';
ALTER TABLE fortune_artifact ALTER COLUMN fortune_image_url DROP DEFAULT;

ALTER TABLE cs_inquiry ADD COLUMN title VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE cs_inquiry ALTER COLUMN title DROP DEFAULT;
ALTER TABLE cs_inquiry ALTER COLUMN content DROP NOT NULL;

ALTER TABLE community_memo_report RENAME CONSTRAINT uq_memo_report_user TO uq_community_memo_report_memo_user;

-- Re-add only the foreign keys retained by the current baseline schema.
ALTER TABLE fortune_artifact
    ADD CONSTRAINT fk_artifact_to_fortune_artifact FOREIGN KEY (artifact_id) REFERENCES artifact (id);

ALTER TABLE flipbook_artifact
    ADD CONSTRAINT fk_artifact_to_flipbook_artifact FOREIGN KEY (artifact_id) REFERENCES artifact (id);

ALTER TABLE infinite_canvas_artifact
    ADD CONSTRAINT fk_artifact_to_infinite_canvas_artifact FOREIGN KEY (artifact_id) REFERENCES artifact (id);

ALTER TABLE phone_artifact
    ADD CONSTRAINT fk_artifact_to_phone_artifact FOREIGN KEY (artifact_id) REFERENCES artifact (id);

ALTER TABLE relay_drawing_artifact
    ADD CONSTRAINT fk_artifact_to_relay_drawing_artifact FOREIGN KEY (artifact_id) REFERENCES artifact (id);
