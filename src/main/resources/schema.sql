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

CREATE TABLE IF NOT EXISTS calendar_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    location VARCHAR(255) NULL,
    description TEXT NULL,
    meeting_url VARCHAR(500) NULL,
    reminder_minutes INT NULL,
    source VARCHAR(50) NOT NULL DEFAULT 'AGENT',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    idempotency_key VARCHAR(100) NULL,
    created_by_task_id VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_calendar_idempotency (user_id, idempotency_key),
    KEY idx_calendar_user_time (user_id, start_time, end_time),
    KEY idx_calendar_user_status_time (user_id, status, start_time, end_time),
    KEY idx_calendar_user_title (user_id, title),
    KEY idx_calendar_task (created_by_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS agent_task (
    task_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(100) NOT NULL,
    input_type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    request_text TEXT NOT NULL,
    intent VARCHAR(50) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'CREATED',
    need_confirm TINYINT(1) NOT NULL DEFAULT 0,
    confirm_token VARCHAR(100) NULL,
    reply_text TEXT NULL,
    speak_text TEXT NULL,
    error_message VARCHAR(1000) NULL,
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (task_id),
    KEY idx_agent_task_user_session (user_id, session_id, created_at),
    KEY idx_agent_task_status (status),
    KEY idx_agent_task_confirm_token (confirm_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS agent_step (
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
    UNIQUE KEY uk_agent_step_order (task_id, step_order),
    KEY idx_agent_step_task (task_id),
    KEY idx_agent_step_status (status),
    KEY idx_agent_step_skill (skill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS agent_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id VARCHAR(64) NOT NULL,
    step_id BIGINT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_level VARCHAR(20) NOT NULL DEFAULT 'INFO',
    message VARCHAR(1000) NOT NULL,
    detail_json TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_agent_event_task_time (task_id, created_at),
    KEY idx_agent_event_step (step_id),
    KEY idx_agent_event_level (event_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS pending_action (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(100) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    confirm_token VARCHAR(100) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expire_at DATETIME NOT NULL,
    executed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pending_confirm_token (confirm_token),
    KEY idx_pending_session_status (user_id, session_id, status, created_at),
    KEY idx_pending_task (task_id),
    KEY idx_pending_expire (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS scheduled_job (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    event_id BIGINT NULL,
    task_id VARCHAR(64) NULL,
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
    KEY idx_scheduled_job_status_run_at (status, run_at),
    KEY idx_scheduled_job_user_run_at (user_id, run_at),
    KEY idx_scheduled_job_event (event_id),
    KEY idx_scheduled_job_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO users (id, username, display_name, timezone, status)
VALUES (1, 'default', 'Default User', 'Asia/Shanghai', 'ACTIVE')
ON DUPLICATE KEY UPDATE updated_at = updated_at;
