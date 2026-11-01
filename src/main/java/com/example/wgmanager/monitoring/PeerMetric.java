package com.example.wgmanager.monitoring;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="peer_metrics")
public class PeerMetric {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="peer_id",nullable=false) private UUID peerId;
    @Column(name="ts",nullable=false) private Instant timestamp;
    @Column(name="rx_bytes",nullable=false) private long rxBytes;
    @Column(name="tx_bytes",nullable=false) private long txBytes;
    @Column(name="last_handshake") private Instant lastHandshake;
    protected PeerMetric(){}
    public PeerMetric(UUID peerId,Instant timestamp,long rxBytes,long txBytes,Instant lastHandshake){this.peerId=peerId;this.timestamp=timestamp;this.rxBytes=rxBytes;this.txBytes=txBytes;this.lastHandshake=lastHandshake;}
    public UUID getId(){return id;} public UUID getPeerId(){return peerId;} public Instant getTimestamp(){return timestamp;}
    public long getRxBytes(){return rxBytes;} public long getTxBytes(){return txBytes;} public Instant getLastHandshake(){return lastHandshake;}
}
