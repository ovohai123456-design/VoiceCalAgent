ALTER TABLE calendar_event
    ADD COLUMN meeting_provider VARCHAR(50) NULL AFTER meeting_url,
    ADD COLUMN meeting_code VARCHAR(32) NULL AFTER meeting_provider;
