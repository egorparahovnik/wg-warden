ALTER TABLE servers ADD COLUMN connection_error VARCHAR(255);

CREATE TABLE server_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    server_id UUID NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    ts TIMESTAMPTZ NOT NULL DEFAULT now(),
    cpu DOUBLE PRECISION NOT NULL,
    ram DOUBLE PRECISION NOT NULL,
    disk DOUBLE PRECISION NOT NULL,
    net_rx BIGINT NOT NULL,
    net_tx BIGINT NOT NULL
);
CREATE INDEX idx_server_metrics_server_ts ON server_metrics(server_id, ts DESC);

CREATE TABLE peer_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    peer_id UUID NOT NULL REFERENCES peers(id) ON DELETE CASCADE,
    ts TIMESTAMPTZ NOT NULL DEFAULT now(),
    rx_bytes BIGINT NOT NULL,
    tx_bytes BIGINT NOT NULL,
    last_handshake TIMESTAMPTZ
);
CREATE INDEX idx_peer_metrics_peer_ts ON peer_metrics(peer_id, ts DESC);
