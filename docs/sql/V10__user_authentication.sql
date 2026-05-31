ALTER TABLE users
    ADD COLUMN password_hash VARCHAR(100) NULL AFTER username;
