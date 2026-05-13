INSERT INTO backoffice_setting (setting_key, setting_value, updated_by, created_at, updated_at)
VALUES (
    'community.report_hide_threshold',
    '{"value":5,"unit":"count","description":"Community memo auto-hide report threshold"}',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (setting_key) DO NOTHING;
