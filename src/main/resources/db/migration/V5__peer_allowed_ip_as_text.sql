ALTER TABLE peers
    ALTER COLUMN allowed_ip TYPE VARCHAR(64) USING allowed_ip::text;
