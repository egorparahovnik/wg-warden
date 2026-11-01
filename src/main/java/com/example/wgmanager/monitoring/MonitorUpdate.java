package com.example.wgmanager.monitoring;
import java.util.List;
import java.util.UUID;
public record MonitorUpdate(UUID serverId,String status,String connectionError,ServerMetricResponse server,List<PeerUpdate> peers){
    public record PeerUpdate(UUID peerId,PeerMetricResponse metrics){}
}
