CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE servers (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name                  VARCHAR(100) NOT NULL,
    host                  VARCHAR(255) NOT NULL,
    ssh_port              INT          NOT NULL DEFAULT 22,
    ssh_user              VARCHAR(64)  NOT NULL,
    encrypted_credentials BYTEA        NOT NULL,
    host_key_fingerprint  VARCHAR(255),
    status                VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_servers_user ON servers (user_id);

CREATE TABLE wg_interfaces (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    server_id             UUID        NOT NULL REFERENCES servers (id) ON DELETE CASCADE,
    name                  VARCHAR(15) NOT NULL DEFAULT 'wg0',
    listen_port           INT         NOT NULL DEFAULT 51820,
    subnet                CIDR        NOT NULL,
    public_key            VARCHAR(64) NOT NULL,
    encrypted_private_key BYTEA       NOT NULL,
    UNIQUE (server_id, name)
);

CREATE TABLE peers (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    interface_id UUID         NOT NULL REFERENCES wg_interfaces (id) ON DELETE CASCADE,
    name         VARCHAR(100) NOT NULL,
    public_key   VARCHAR(64)  NOT NULL,
    allowed_ip   INET         NOT NULL,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    expires_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (interface_id, allowed_ip),   -- защита от гонок при выдаче IP
    UNIQUE (interface_id, public_key)
);
