USE voicecal;

-- Run once before starting the refactored backend.
-- Old tables are intentionally retained for rollback and manual verification.

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

INSERT IGNORE INTO command_task (
    task_id, user_id, session_id, input_type, input_text, intent, status,
    reply_text, speak_text, error_message, started_at, finished_at, created_at, updated_at
)
SELECT
    task_id, user_id, session_id, input_type, request_text, intent, status,
    reply_text, speak_text, error_message, started_at, finished_at, created_at, updated_at
FROM agent_task;

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

INSERT IGNORE INTO command_action (
    action_id, task_id, action_type, payload_json, status, executed_at, created_at, updated_at
)
SELECT
    CONCAT('action_legacy_', id),
    task_id,
    action_type,
    payload_json,
    CASE status
        WHEN 'PENDING' THEN 'WAITING_CONFIRM'
        ELSE status
    END,
    executed_at,
    created_at,
    updated_at
FROM pending_action;

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

INSERT IGNORE INTO pending_confirmation (
    id, action_id, user_id, session_id, confirm_token, status, expire_at,
    confirmed_at, canceled_at, created_at, updated_at
)
SELECT
    id,
    CONCAT('action_legacy_', id),
    user_id,
    session_id,
    confirm_token,
    CASE status
        WHEN 'EXECUTED' THEN 'CONFIRMED'
        ELSE status
    END,
    expire_at,
    CASE WHEN status = 'EXECUTED' THEN executed_at ELSE NULL END,
    NULL,
    created_at,
    updated_at
FROM pending_action;

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

INSERT IGNORE INTO execution_log (
    id, task_id, step_order, skill_id, step_name, executor_type, request_json,
    response_json, status, latency_ms, error_message, started_at, finished_at, created_at
)
SELECT
    id, task_id, step_order, skill_id, step_name, executor_type, request_json,
    response_json, status, latency_ms, error_message, started_at, finished_at, created_at
FROM agent_step;

SET @source_task_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'calendar_event'
      AND COLUMN_NAME = 'source_task_id'
);
SET @add_source_task_column_sql = IF(
    @source_task_column_exists = 0,
    'ALTER TABLE calendar_event ADD COLUMN source_task_id VARCHAR(64) NULL AFTER idempotency_key',
    'SELECT 1'
);
PREPARE add_source_task_column_statement FROM @add_source_task_column_sql;
EXECUTE add_source_task_column_statement;
DEALLOCATE PREPARE add_source_task_column_statement;

SET @legacy_task_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'calendar_event'
      AND COLUMN_NAME = 'created_by_task_id'
);
SET @copy_source_task_sql = IF(
    @legacy_task_column_exists > 0,
    'UPDATE calendar_event SET source_task_id = created_by_task_id WHERE source_task_id IS NULL',
    'SELECT 1'
);
PREPARE copy_source_task_statement FROM @copy_source_task_sql;
EXECUTE copy_source_task_statement;
DEALLOCATE PREPARE copy_source_task_statement;

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

INSERT IGNORE INTO reminder_job (
    id, user_id, event_id, job_type, job_payload_json, run_at, status,
    retry_count, max_retry_count, last_error, locked_at, executed_at, created_at, updated_at
)
SELECT
    id, user_id, event_id, job_type, job_payload_json, run_at, status,
    retry_count, max_retry_count, last_error, locked_at, executed_at, created_at, updated_at
FROM scheduled_job
WHERE event_id IS NOT NULL;
