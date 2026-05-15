INSERT INTO backoffice_setting (setting_key, setting_value, updated_by, created_at, updated_at)
VALUES
    (
        'infinite_canvas.participant_limit',
        '{"min":1,"max":6,"unit":"people","description":"Infinite canvas concurrent participant limit"}',
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    )
ON CONFLICT (setting_key) DO NOTHING;
