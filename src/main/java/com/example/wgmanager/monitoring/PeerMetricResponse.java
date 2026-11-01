package com.example.wgmanager.monitoring;
import java.time.Instant;
public record PeerMetricResponse(Instant timestamp,long rxBytes,long txBytes,Instant lastHandshake,boolean online){
    static PeerMetricResponse from(PeerMetric metric){Instant handshake=metric.getLastHandshake();return new PeerMetricResponse(metric.getTimestamp(),metric.getRxBytes(),metric.getTxBytes(),handshake,handshake!=null&&handshake.isAfter(Instant.now().minusSeconds(180)));}
}
