package com.example.wgwarden.monitoring;
import java.time.Instant;
public record ServerMetricResponse(Instant timestamp,double cpu,double ram,double disk,long netRx,long netTx){
    static ServerMetricResponse from(ServerMetric metric){return new ServerMetricResponse(metric.getTimestamp(),metric.getCpu(),metric.getRam(),metric.getDisk(),metric.getNetRx(),metric.getNetTx());}
}
