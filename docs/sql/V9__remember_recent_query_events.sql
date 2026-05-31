USE voicecal;

ALTER TABLE conversation_session_context
    ADD COLUMN last_query_event_ids_json TEXT NULL AFTER last_mentioned_event_id;
