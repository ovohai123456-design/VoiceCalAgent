USE voicecal;

ALTER TABLE command_task
    DROP INDEX idx_command_task_user_session,
    ADD INDEX idx_command_task_user_session_status_created (user_id, session_id, status, created_at);
