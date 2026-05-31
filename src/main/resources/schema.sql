CREATE DATABASE IF NOT EXISTS voicecal
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE voicecal;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    display_name VARCHAR(100) NULL,
    phone VARCHAR(32) NULL,
    email VARCHAR(128) NULL,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    KEY idx_users_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS command_task (
    task_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(100) NOT NULL,
    input_type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    input_text TEXT NOT NULL,
    intent VARCHAR(50) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'CREATED',
    reply_text TEXT NULL,
    speak_text TEXT NULL,
    error_message VARCHAR(1000) NULL,
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (task_id),
    KEY idx_command_task_user_session (user_id, session_id, created_at),
    KEY idx_command_task_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS command_action (
    action_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PREPARED',
    error_message VARCHAR(1000) NULL,
    executed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (action_id),
    KEY idx_command_action_task (task_id),
    KEY idx_command_action_status (status),
    CONSTRAINT fk_command_action_task FOREIGN KEY (task_id) REFERENCES command_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS pending_confirmation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    action_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(100) NOT NULL,
    confirm_token VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expire_at DATETIME NOT NULL,
    confirmed_at DATETIME NULL,
    canceled_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_confirmation_token (confirm_token),
    KEY idx_confirmation_action (action_id),
    KEY idx_confirmation_session_status (user_id, session_id, status, created_at),
    KEY idx_confirmation_expire (expire_at),
    CONSTRAINT fk_confirmation_action FOREIGN KEY (action_id) REFERENCES command_action (action_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS calendar_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    location VARCHAR(255) NULL,
    description TEXT NULL,
    meeting_url VARCHAR(500) NULL,
    meeting_provider VARCHAR(50) NULL,
    meeting_code VARCHAR(32) NULL,
    reminder_minutes INT NULL,
    source VARCHAR(50) NOT NULL DEFAULT 'AGENT',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    idempotency_key VARCHAR(100) NULL,
    source_task_id VARCHAR(64) NULL,
    recurrence_series_id VARCHAR(64) NULL,
    recurrence_index INT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_calendar_idempotency (user_id, idempotency_key),
    KEY idx_calendar_user_time (user_id, start_time, end_time),
    KEY idx_calendar_user_status_time (user_id, status, start_time, end_time),
    KEY idx_calendar_user_title (user_id, title),
    KEY idx_calendar_source_task (source_task_id)
    ,KEY idx_calendar_recurrence_series (recurrence_series_id, recurrence_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS recurrence_series (
    series_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    recurrence_type VARCHAR(20) NOT NULL,
    interval_value INT NOT NULL DEFAULT 1,
    total_count INT NOT NULL,
    until_date DATE NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (series_id),
    KEY idx_recurrence_series_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reminder_job (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    job_type VARCHAR(50) NOT NULL,
    job_payload_json TEXT NULL,
    run_at DATETIME NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    max_retry_count INT NOT NULL DEFAULT 3,
    last_error VARCHAR(1000) NULL,
    locked_at DATETIME NULL,
    executed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_reminder_job_status_run_at (status, run_at),
    KEY idx_reminder_job_user_run_at (user_id, run_at),
    KEY idx_reminder_job_event (event_id),
    CONSTRAINT fk_reminder_job_event FOREIGN KEY (event_id) REFERENCES calendar_event (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS execution_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id VARCHAR(64) NOT NULL,
    step_order INT NOT NULL,
    skill_id VARCHAR(100) NULL,
    step_name VARCHAR(100) NOT NULL,
    executor_type VARCHAR(30) NULL,
    request_json TEXT NULL,
    response_json TEXT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'CREATED',
    latency_ms BIGINT NULL,
    error_message VARCHAR(1000) NULL,
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_execution_log_order (task_id, step_order),
    KEY idx_execution_log_task (task_id),
    KEY idx_execution_log_status (status),
    KEY idx_execution_log_skill (skill_id),
    CONSTRAINT fk_execution_log_task FOREIGN KEY (task_id) REFERENCES command_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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

INSERT INTO users (id, username, display_name, timezone, status)
VALUES (1, 'default', 'Default User', 'Asia/Shanghai', 'ACTIVE')
ON DUPLICATE KEY UPDATE updated_at = updated_at;

INSERT INTO contact (user_id, name, phone, email)
SELECT 1, '张三', '13800000001', 'zhangsan@example.com'
WHERE NOT EXISTS (
    SELECT 1 FROM contact WHERE user_id = 1 AND name = '张三'
);

INSERT INTO user_preference (
    user_id,
    default_duration_minutes,
    default_reminder_minutes,
    default_location,
    default_email
)
VALUES (1, 60, 10, '默认会议室', 'default@example.com')
ON DUPLICATE KEY UPDATE user_id = user_id;

CREATE TABLE IF NOT EXISTS conversation_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    task_id VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_conv_msg_user_session_time (user_id, session_id, created_at),
    KEY idx_conv_msg_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS conversation_state (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(100) NOT NULL,
    state_type VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    task_id VARCHAR(64) NULL,
    action_id VARCHAR(64) NULL,
    confirm_token VARCHAR(100) NULL,
    context_json TEXT NULL,
    expire_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_conv_state_user_session (user_id, session_id, status, created_at),
    KEY idx_conv_state_task (task_id),
    KEY idx_conv_state_expire (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS conversation_session_context (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(100) NOT NULL,
    last_mentioned_event_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_conv_session_context (user_id, session_id),
    KEY idx_conv_context_event (last_mentioned_event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
