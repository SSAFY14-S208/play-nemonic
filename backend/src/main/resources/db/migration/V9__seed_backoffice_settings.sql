INSERT INTO backoffice_setting (setting_key, setting_value, updated_by, created_at, updated_at)
VALUES
    (
        'community.max_memo_count',
        '{"value":50,"unit":"count","description":"Community canvas visible memo limit"}',
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        'relay.room_participant_limit',
        '{"min":2,"max":6,"unit":"people","description":"Relay room participant limit"}',
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        'relay.room_time_limit_seconds',
        '{"default":45,"allowed":[30,45,60],"unit":"seconds","description":"Relay room drawing time limit"}',
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        'relay.reconnect_grace_seconds',
        '{"value":10,"unit":"seconds","description":"Relay playing-room reconnect grace period"}',
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        'flipbook.room_participant_limit',
        '{"min":2,"max":6,"unit":"people","description":"Flipbook room participant limit"}',
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        'flipbook.room_time_limit_seconds',
        '{"default":45,"allowed":[30,45,60],"unit":"seconds","description":"Flipbook room drawing time limit"}',
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        'flipbook.min_frames_per_flipbook',
        '{"value":8,"unit":"frames","description":"Minimum frames per completed flipbook"}',
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        'flipbook.reconnect_grace_seconds',
        '{"value":10,"unit":"seconds","description":"Flipbook playing-room reconnect grace period"}',
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        'fortune.daily_limit',
        '{"value":1,"unit":"count","description":"Daily fortune generation limit per anonymous user"}',
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        'cs_inquiry.unresolved_alert_threshold_hours',
        '{"value":24,"unit":"hours","description":"Unresolved customer inquiry alert threshold"}',
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    )
ON CONFLICT (setting_key) DO NOTHING;
