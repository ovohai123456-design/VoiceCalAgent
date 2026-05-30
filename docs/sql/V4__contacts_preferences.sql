USE voicecal;

CREATE TABLE IF NOT EXISTS contact (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(32) NULL,
    email VARCHAR(128) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_contact_user_name (user_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_preference (
    user_id BIGINT NOT NULL,
    default_duration_minutes INT NULL,
    default_reminder_minutes INT NULL,
    default_location VARCHAR(255) NULL,
    default_email VARCHAR(128) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO contact (user_id, name, phone, email)
SELECT 1, '张三', '13800000001', 'zhangsan@example.com'
WHERE NOT EXISTS (
    SELECT 1 FROM contact WHERE user_id = 1 AND name = '张三'
);

INSERT INTO user_preference (
    user_id, default_duration_minutes, default_reminder_minutes, default_location, default_email
) VALUES (
    1, 60, 10, '默认会议室', 'default@example.com'
) ON DUPLICATE KEY UPDATE user_id = user_id;
