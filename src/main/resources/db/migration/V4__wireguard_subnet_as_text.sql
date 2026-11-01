ALTER TABLE wg_interfaces
    ALTER COLUMN subnet TYPE VARCHAR(64) USING subnet::text;
