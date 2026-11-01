package com.example.wgmanager.monitoring;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="server_metrics")
public class ServerMetric {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="server_id",nullable=false) private UUID serverId;
    @Column(name="ts",nullable=false) private Instant timestamp;
    @Column(nullable=false) private double cpu;
    @Column(nullable=false) private double ram;
    @Column(nullable=false) private double disk;
    @Column(name="net_rx",nullable=false) private long netRx;
    @Column(name="net_tx",nullable=false) private long netTx;
    protected ServerMetric(){}
    public ServerMetric(UUID serverId,Instant timestamp,double cpu,double ram,double disk,long netRx,long netTx){this.serverId=serverId;this.timestamp=timestamp;this.cpu=cpu;this.ram=ram;this.disk=disk;this.netRx=netRx;this.netTx=netTx;}
    public UUID getId(){return id;} public UUID getServerId(){return serverId;} public Instant getTimestamp(){return timestamp;}
    public double getCpu(){return cpu;} public double getRam(){return ram;} public double getDisk(){return disk;} public long getNetRx(){return netRx;} public long getNetTx(){return netTx;}
}
