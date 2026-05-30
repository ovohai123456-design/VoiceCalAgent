USE voicecal;

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

SET @recurrence_series_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'calendar_event'
      AND COLUMN_NAME = 'recurrence_series_id'
);
SET @add_recurrence_series_column_sql = IF(
    @recurrence_series_column_exists = 0,
    'ALTER TABLE calendar_event ADD COLUMN recurrence_series_id VARCHAR(64) NULL AFTER source_task_id',
    'SELECT 1'
);
PREPARE add_recurrence_series_column_statement FROM @add_recurrence_series_column_sql;
EXECUTE add_recurrence_series_column_statement;
DEALLOCATE PREPARE add_recurrence_series_column_statement;

SET @recurrence_index_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'calendar_event'
      AND COLUMN_NAME = 'recurrence_index'
);
SET @add_recurrence_index_column_sql = IF(
    @recurrence_index_column_exists = 0,
    'ALTER TABLE calendar_event ADD COLUMN recurrence_index INT NULL AFTER recurrence_series_id',
    'SELECT 1'
);
PREPARE add_recurrence_index_column_statement FROM @add_recurrence_index_column_sql;
EXECUTE add_recurrence_index_column_statement;
DEALLOCATE PREPARE add_recurrence_index_column_statement;

SET @recurrence_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'calendar_event'
      AND INDEX_NAME = 'idx_calendar_recurrence_series'
);
SET @add_recurrence_index_sql = IF(
    @recurrence_index_exists = 0,
    'ALTER TABLE calendar_event ADD KEY idx_calendar_recurrence_series (recurrence_series_id, recurrence_index)',
    'SELECT 1'
);
PREPARE add_recurrence_index_statement FROM @add_recurrence_index_sql;
EXECUTE add_recurrence_index_statement;
DEALLOCATE PREPARE add_recurrence_index_statement;
