-- ============================================================
-- ENUM 타입 정의
-- ============================================================

CREATE TYPE asset_role_type       AS ENUM ('background', 'icon', 'sticker', 'frame', 'badge');
CREATE TYPE prompt_feature_type   AS ENUM ('fortune', 'sticker');
CREATE TYPE report_reason_type    AS ENUM ('inappropriate', 'spam', 'other');
CREATE TYPE inquiry_type_type     AS ENUM ('error', 'feature_request', 'content_report', 'other');
CREATE TYPE inquiry_status_type   AS ENUM ('new', 'in_progress', 'resolved', 'closed');
CREATE TYPE hidden_reason_type    AS ENUM ('report_threshold', 'ai_moderation');
CREATE TYPE moderation_status_type AS ENUM ('pending', 'allowed', 'blocked');
CREATE TYPE artifact_kind_type    AS ENUM ('relay_drawing', 'flipbook', 'fortune', 'infinite_canvas', 'community_memo', 'phone');
CREATE TYPE admin_role_type       AS ENUM ('admin', 'super_admin');
CREATE TYPE deleted_reason_type   AS ENUM ('expired', 'admin_removed', 'user_delete');

-- ============================================================
-- 테이블 생성
-- ============================================================

CREATE TABLE app_user (
    id          UUID          NOT NULL,
    nickname    VARCHAR(10)   NOT NULL,
    last_seen_at TIMESTAMP    NOT NULL,
    birthday    DATE          NULL,
    birthtime   TIME          NULL,
    user_agent  TEXT          NOT NULL,
    created_at  TIMESTAMP     NOT NULL,
    updated_at  TIMESTAMP     NOT NULL,
    CONSTRAINT pk_app_user PRIMARY KEY (id)
);

CREATE TABLE admin_user (
    id              BIGSERIAL       NOT NULL,
    login_id        VARCHAR(64)     NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    nickname        VARCHAR(20)     NOT NULL,
    email           VARCHAR(255)    NOT NULL,
    role            admin_role_type NOT NULL,
    last_login_at   TIMESTAMP       NULL,
    created_at      TIMESTAMP       NOT NULL,
    updated_at      TIMESTAMP       NOT NULL,
    deleted_at      TIMESTAMP       NULL,
    CONSTRAINT pk_admin_user PRIMARY KEY (id)
);

CREATE TABLE artifact (
    id              UUID                NOT NULL,
    kind            artifact_kind_type  NOT NULL,
    source_room_id  VARCHAR(64)         NULL,
    thumbnail_url   VARCHAR(200)        NOT NULL,
    meta            TEXT                NOT NULL DEFAULT '{}',
    created_at      TIMESTAMP           NOT NULL,
    updated_at      TIMESTAMP           NOT NULL,
    CONSTRAINT pk_artifact PRIMARY KEY (id)
);

CREATE TABLE fortune_artifact (
    artifact_id         UUID            NOT NULL,
    description         TEXT            NOT NULL,
    fortune_image_url   VARCHAR(200)    NOT NULL,
    CONSTRAINT pk_fortune_artifact PRIMARY KEY (artifact_id),
    CONSTRAINT fk_artifact_to_fortune_artifact FOREIGN KEY (artifact_id) REFERENCES artifact (id)
);

CREATE TABLE fortune_asset (
    id              BIGSERIAL   NOT NULL,
    icon_image_url  TEXT        NOT NULL,
    created_at      TIMESTAMP   NOT NULL,
    updated_at      TIMESTAMP   NOT NULL,
    CONSTRAINT pk_fortune_asset PRIMARY KEY (id)
);

CREATE TABLE fortune_artifact_asset (
    id          BIGSERIAL           NOT NULL,
    asset_role  asset_role_type     NOT NULL,
    asset_id    BIGINT              NOT NULL,
    artifact_id UUID                NOT NULL,
    CONSTRAINT pk_fortune_artifact_asset PRIMARY KEY (id)
);

CREATE TABLE flipbook_artifact (
    artifact_id UUID            NOT NULL,
    gif_url     VARCHAR(200)    NOT NULL,
    first_image VARCHAR(200)    NULL,
    CONSTRAINT pk_flipbook_artifact PRIMARY KEY (artifact_id),
    CONSTRAINT fk_artifact_to_flipbook_artifact FOREIGN KEY (artifact_id) REFERENCES artifact (id)
);

CREATE TABLE infinite_canvas_artifact (
    artifact_id         UUID            NOT NULL,
    canvas_image_url    VARCHAR(200)    NOT NULL,
    CONSTRAINT pk_infinite_canvas_artifact PRIMARY KEY (artifact_id),
    CONSTRAINT fk_artifact_to_infinite_canvas_artifact FOREIGN KEY (artifact_id) REFERENCES artifact (id)
);

CREATE TABLE phone_artifact (
    artifact_id         UUID            NOT NULL,
    phone_image_url     VARCHAR(200)    NOT NULL,
    CONSTRAINT pk_phone_artifact PRIMARY KEY (artifact_id),
    CONSTRAINT fk_artifact_to_phone_artifact FOREIGN KEY (artifact_id) REFERENCES artifact (id)
);

CREATE TABLE relay_drawing_artifact (
    artifact_id             UUID            NOT NULL,
    combined_preview_url    VARCHAR(200)    NULL,
    CONSTRAINT pk_relay_drawing_artifact PRIMARY KEY (artifact_id),
    CONSTRAINT fk_artifact_to_relay_drawing_artifact FOREIGN KEY (artifact_id) REFERENCES artifact (id)
);

CREATE TABLE gallery (
    id          UUID        NOT NULL,
    user_id     UUID        NOT NULL,
    artifact_id UUID        NOT NULL,
    deleted_at  TIMESTAMP   NULL,
    CONSTRAINT pk_gallery PRIMARY KEY (id),
    CONSTRAINT uq_gallery_user_artifact UNIQUE (user_id, artifact_id)
);

CREATE TABLE community_memo (
    id                      UUID                        NOT NULL,
    user_id                 UUID                        NOT NULL,
    artifact_id             UUID                        NULL,
    position_x              DOUBLE PRECISION            NOT NULL,
    position_y              DOUBLE PRECISION            NOT NULL,
    z_index                 INT                         NOT NULL,
    rotation_deg            REAL                        NOT NULL,
    decoration              TEXT                        NOT NULL DEFAULT '{}',
    body_image_url          TEXT                        NULL,
    attached_at             TIMESTAMP                   NOT NULL,
    report_count            INT                         NOT NULL DEFAULT 0,
    is_hidden               BOOLEAN                     NOT NULL DEFAULT FALSE,
    hidden_reason           hidden_reason_type          NULL,
    hidden_at               TIMESTAMP                   NULL,
    moderation_status       moderation_status_type      NOT NULL DEFAULT 'pending',
    ocr_text                TEXT                        NULL,
    ocr_categories          TEXT                        NULL,
    moderation_checked_at   TIMESTAMP                   NULL,
    reviewed_by             BIGINT                      NULL,
    created_at              TIMESTAMP                   NOT NULL,
    updated_at              TIMESTAMP                   NOT NULL,
    deleted_at              TIMESTAMP                   NULL,
    deleted_reason          deleted_reason_type         NULL,
    CONSTRAINT pk_community_memo PRIMARY KEY (id)
);

CREATE TABLE community_memo_report (
    id              BIGSERIAL           NOT NULL,
    memo_id         UUID                NOT NULL,
    user_id         UUID                NOT NULL,
    reason          report_reason_type  NOT NULL,
    reason_detail   TEXT                NULL,
    created_at      TIMESTAMP           NOT NULL,
    CONSTRAINT pk_community_memo_report PRIMARY KEY (id),
    CONSTRAINT uq_community_memo_report_memo_user UNIQUE (memo_id, user_id)
);

CREATE TABLE cs_inquiry (
    id              BIGSERIAL           NOT NULL,
    user_id         UUID                NOT NULL,
    title           VARCHAR(255)        NOT NULL,
    inquiry_type    inquiry_type_type   NOT NULL,
    content         VARCHAR(1000)       NULL,
    email           VARCHAR(255)        NULL,
    attachments     TEXT                NULL,
    meta            TEXT                NULL DEFAULT '{}',
    status          inquiry_status_type NOT NULL DEFAULT 'new',
    assigned_to     BIGINT              NULL,
    response_note   TEXT                NULL,
    responded_at    TIMESTAMP           NULL,
    created_at      TIMESTAMP           NOT NULL,
    updated_at      TIMESTAMP           NOT NULL,
    CONSTRAINT pk_cs_inquiry PRIMARY KEY (id)
);

CREATE TABLE backoffice_setting (
    id              BIGSERIAL       NOT NULL,
    setting_key     VARCHAR(128)    NOT NULL,
    setting_value   TEXT            NOT NULL DEFAULT '{}',
    updated_by      BIGINT          NOT NULL,
    created_at      TIMESTAMP       NOT NULL,
    updated_at      TIMESTAMP       NOT NULL,
    CONSTRAINT pk_backoffice_setting PRIMARY KEY (id),
    CONSTRAINT uq_backoffice_setting_key UNIQUE (setting_key)
);

CREATE TABLE gms_prompt_template (
    id              BIGSERIAL           NOT NULL,
    prompt_name     VARCHAR(64)         NOT NULL,
    template_text   TEXT                NOT NULL,
    feature_type    prompt_feature_type NOT NULL,
    created_by      BIGINT              NOT NULL,
    created_at      TIMESTAMP           NOT NULL,
    updated_at      TIMESTAMP           NOT NULL,
    deleted_at      TIMESTAMP           NULL,
    CONSTRAINT pk_gms_prompt_template PRIMARY KEY (id),
    CONSTRAINT uq_gms_prompt_template_name UNIQUE (prompt_name)
);
