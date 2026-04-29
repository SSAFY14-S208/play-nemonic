-- =============================================
-- ENUM TYPES
-- =============================================

CREATE TYPE inquiry_type_enum      AS ENUM ('error', 'feature_request', 'content_report', 'other');
CREATE TYPE inquiry_status_enum    AS ENUM ('new', 'in_progress', 'resolved', 'closed');
CREATE TYPE artifact_kind_enum     AS ENUM ('relay_drawing', 'flipbook', 'fortune', 'infinite_canvas', 'community_memo', 'phone');
CREATE TYPE asset_role_enum        AS ENUM ('background', 'icon', 'sticker', 'frame', 'badge');
CREATE TYPE feature_type_enum      AS ENUM ('fortune', 'sticker');
CREATE TYPE admin_role_enum        AS ENUM ('admin', 'super_admin');
CREATE TYPE hidden_reason_enum     AS ENUM ('report_threshold', 'ai_moderation');
CREATE TYPE moderation_status_enum AS ENUM ('pending', 'allowed', 'blocked');
CREATE TYPE report_reason_enum     AS ENUM ('inappropriate', 'spam', 'other');
CREATE TYPE deleted_reason_enum    AS ENUM ('expired', 'admin_removed', 'user_delete');

-- =============================================
-- CORE USER TABLES
-- =============================================

CREATE TABLE app_user (
    id           VARCHAR(36)  NOT NULL,
    nickname     VARCHAR(10)  NOT NULL,
    last_seen_at TIMESTAMP    NOT NULL,
    birthday     DATE         NULL,
    birthtime    TIME         NULL,
    user_agent   TEXT         NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    CONSTRAINT PK_APP_USER PRIMARY KEY (id)
);

CREATE TABLE admin_user (
    id             BIGSERIAL    NOT NULL,
    login_id       VARCHAR(64)  NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    nickname       VARCHAR(20)  NOT NULL,
    email          VARCHAR(255) NOT NULL,
    role           admin_role_enum NOT NULL,
    last_login_at  TIMESTAMP    NULL,
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL,
    deleted_at     TIMESTAMP    NULL,
    CONSTRAINT PK_ADMIN_USER PRIMARY KEY (id)
);

-- =============================================
-- ARTIFACT (부모 테이블)
-- =============================================

CREATE TABLE artifact (
    id             VARCHAR(36)       NOT NULL,
    kind           artifact_kind_enum NOT NULL,
    source_room_id VARCHAR(64)       NULL,
    thumbnail_url  VARCHAR(200)      NOT NULL,
    meta           TEXT              NOT NULL DEFAULT '{}',
    created_at     TIMESTAMP         NOT NULL,
    updated_at     TIMESTAMP         NOT NULL,
    CONSTRAINT PK_ARTIFACT PRIMARY KEY (id)
);

-- =============================================
-- ARTIFACT 서브타입 테이블
-- =============================================

CREATE TABLE infinite_canvas_artifact (
    artifact_id      VARCHAR(36)  NOT NULL,
    aspect_ratio     VARCHAR(16)  NOT NULL,
    canvas_image_url VARCHAR(200) NOT NULL,
    CONSTRAINT PK_INFINITE_CANVAS_ARTIFACT PRIMARY KEY (artifact_id),
    CONSTRAINT FK_artifact_TO_infinite_canvas_artifact FOREIGN KEY (artifact_id) REFERENCES artifact (id)
);

CREATE TABLE flipbook_artifact (
    artifact_id VARCHAR(36)  NOT NULL,
    gif_url     VARCHAR(200) NOT NULL,
    first_image VARCHAR(200) NULL,
    CONSTRAINT PK_FLIPBOOK_ARTIFACT PRIMARY KEY (artifact_id),
    CONSTRAINT FK_artifact_TO_flipbook_artifact FOREIGN KEY (artifact_id) REFERENCES artifact (id)
);

CREATE TABLE relay_drawing_artifact (
    artifact_id          VARCHAR(36)  NOT NULL,
    combined_preview_url VARCHAR(200) NULL,
    CONSTRAINT PK_RELAY_DRAWING_ARTIFACT PRIMARY KEY (artifact_id),
    CONSTRAINT FK_artifact_TO_relay_drawing_artifact FOREIGN KEY (artifact_id) REFERENCES artifact (id)
);

CREATE TABLE phone_artifact (
    artifact_id     VARCHAR(36)  NOT NULL,
    phone_image_url VARCHAR(200) NOT NULL,
    CONSTRAINT PK_PHONE_ARTIFACT PRIMARY KEY (artifact_id),
    CONSTRAINT FK_artifact_TO_phone_artifact FOREIGN KEY (artifact_id) REFERENCES artifact (id)
);

CREATE TABLE fortune_artifact (
    artifact_id VARCHAR(36) NOT NULL,
    description TEXT        NOT NULL,
    CONSTRAINT PK_FORTUNE_ARTIFACT PRIMARY KEY (artifact_id),
    CONSTRAINT FK_artifact_TO_fortune_artifact FOREIGN KEY (artifact_id) REFERENCES artifact (id)
);

-- =============================================
-- FORTUNE 관련
-- =============================================

CREATE TABLE fortune_asset (
    id             BIGSERIAL NOT NULL,
    icon_image_url TEXT      NOT NULL,
    created_at     TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP NOT NULL,
    CONSTRAINT PK_FORTUNE_ASSET PRIMARY KEY (id)
);

CREATE TABLE fortune_artifact_asset (
    id          BIGSERIAL       NOT NULL,
    asset_role  asset_role_enum NOT NULL,
    asset_id    BIGINT          NOT NULL,
    artifact_id VARCHAR(36)     NOT NULL,
    CONSTRAINT PK_FORTUNE_ARTIFACT_ASSET PRIMARY KEY (id),
    CONSTRAINT FK_fortune_artifact_asset_asset    FOREIGN KEY (asset_id)    REFERENCES fortune_asset    (id),
    CONSTRAINT FK_fortune_artifact_asset_artifact FOREIGN KEY (artifact_id) REFERENCES fortune_artifact (artifact_id)
);

-- =============================================
-- GALLERY
-- =============================================

CREATE TABLE gallery (
    id          VARCHAR(36) NOT NULL,
    user_id     VARCHAR(36) NOT NULL,
    artifact_id VARCHAR(36) NOT NULL,
    deleted_at  TIMESTAMP   NULL,
    CONSTRAINT PK_GALLERY               PRIMARY KEY (id),
    CONSTRAINT UQ_GALLERY_USER_ARTIFACT UNIQUE (user_id, artifact_id),
    CONSTRAINT FK_gallery_user          FOREIGN KEY (user_id)     REFERENCES app_user (id),
    CONSTRAINT FK_gallery_artifact      FOREIGN KEY (artifact_id) REFERENCES artifact  (id)
);

-- =============================================
-- COMMUNITY MEMO
-- =============================================

CREATE TABLE community_memo (
    id                    VARCHAR(36)              NOT NULL,
    user_id               VARCHAR(36)              NOT NULL,
    artifact_id           VARCHAR(36)              NULL,
    position_x            DOUBLE PRECISION         NOT NULL,
    position_y            DOUBLE PRECISION         NOT NULL,
    z_index               INT                      NOT NULL,
    rotation_deg          REAL                     NOT NULL,
    decoration            TEXT                     NOT NULL DEFAULT '{}',
    body_image_url        TEXT                     NULL,
    attached_at           TIMESTAMP                NOT NULL,
    report_count          INT                      NOT NULL DEFAULT 0,
    is_hidden             BOOLEAN                  NOT NULL DEFAULT FALSE,
    hidden_reason         hidden_reason_enum       NULL,
    hidden_at             TIMESTAMP                NULL,
    moderation_status     moderation_status_enum   NOT NULL DEFAULT 'pending',
    ocr_text              TEXT                     NULL,
    ocr_categories        TEXT                     NULL,
    moderation_checked_at TIMESTAMP                NULL,
    reviewed_by           BIGINT                   NULL,
    created_at            TIMESTAMP                NOT NULL,
    updated_at            TIMESTAMP                NOT NULL,
    deleted_at            TIMESTAMP                NULL,
    deleted_reason        deleted_reason_enum      NULL,
    CONSTRAINT PK_COMMUNITY_MEMO          PRIMARY KEY (id),
    CONSTRAINT FK_community_memo_user     FOREIGN KEY (user_id)     REFERENCES app_user  (id),
    CONSTRAINT FK_community_memo_artifact FOREIGN KEY (artifact_id) REFERENCES artifact   (id),
    CONSTRAINT FK_community_memo_reviewer FOREIGN KEY (reviewed_by) REFERENCES admin_user (id)
);

CREATE TABLE community_memo_report (
    id            BIGSERIAL          NOT NULL,
    memo_id       VARCHAR(36)        NOT NULL,
    user_id       VARCHAR(36)        NOT NULL,
    reason        report_reason_enum NOT NULL,
    reason_detail TEXT               NULL,
    created_at    TIMESTAMP          NOT NULL,
    CONSTRAINT PK_COMMUNITY_MEMO_REPORT PRIMARY KEY (id),
    CONSTRAINT UQ_MEMO_REPORT_USER      UNIQUE (memo_id, user_id),
    CONSTRAINT FK_memo_report_memo      FOREIGN KEY (memo_id) REFERENCES community_memo (id),
    CONSTRAINT FK_memo_report_user      FOREIGN KEY (user_id) REFERENCES app_user       (id)
);

-- =============================================
-- CS (고객센터)
-- =============================================

CREATE TABLE cs_inquiry (
    id            BIGSERIAL           NOT NULL,
    user_id       VARCHAR(36)         NOT NULL,
    inquiry_type  inquiry_type_enum   NOT NULL,
    content       VARCHAR(1000)       NOT NULL,
    email         VARCHAR(255)        NULL,
    attachments   TEXT                NULL,
    meta          TEXT                NULL DEFAULT '{}',
    status        inquiry_status_enum NOT NULL DEFAULT 'new',
    assigned_to   BIGINT              NULL,
    response_note TEXT                NULL,
    responded_at  TIMESTAMP           NULL,
    created_at    TIMESTAMP           NOT NULL,
    updated_at    TIMESTAMP           NOT NULL,
    CONSTRAINT PK_CS_INQUIRY       PRIMARY KEY (id),
    CONSTRAINT FK_cs_inquiry_user  FOREIGN KEY (user_id)     REFERENCES app_user  (id),
    CONSTRAINT FK_cs_inquiry_admin FOREIGN KEY (assigned_to) REFERENCES admin_user (id)
);

-- =============================================
-- 백오피스 / GMS
-- =============================================

CREATE TABLE backoffice_setting (
    id            BIGSERIAL    NOT NULL,
    setting_key   VARCHAR(128) NOT NULL,
    setting_value TEXT         NOT NULL DEFAULT '{}',
    updated_by    BIGINT       NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT PK_BACKOFFICE_SETTING       PRIMARY KEY (id),
    CONSTRAINT UQ_BACKOFFICE_SETTING_KEY   UNIQUE (setting_key),
    CONSTRAINT FK_backoffice_setting_admin FOREIGN KEY (updated_by) REFERENCES admin_user (id)
);

CREATE TABLE gms_prompt_template (
    id             BIGSERIAL         NOT NULL,
    prompt_name    VARCHAR(64)       NOT NULL,
    template_text  TEXT              NOT NULL,
    feature_type   feature_type_enum NOT NULL,
    created_by     BIGINT            NOT NULL,
    created_at     TIMESTAMP         NOT NULL,
    updated_at     TIMESTAMP         NOT NULL,
    deleted_at     TIMESTAMP         NULL,
    CONSTRAINT PK_GMS_PROMPT_TEMPLATE       PRIMARY KEY (id),
    CONSTRAINT UQ_GMS_PROMPT_TEMPLATE_NAME  UNIQUE (prompt_name),
    CONSTRAINT FK_gms_prompt_template_admin FOREIGN KEY (created_by) REFERENCES admin_user (id)
);
