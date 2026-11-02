package com.example.wgwarden.server;
import java.time.Instant;
import java.util.UUID;
public record ServerResponse(UUID id,String name,String host,int sshPort,String sshUser,String operatingSystem,
 ServerStatus status,String connectionError,String hostKeyFingerprint,Instant createdAt) {
 static ServerResponse from(Server s){return new ServerResponse(s.getId(),s.getName(),s.getHost(),s.getSshPort(),s.getSshUser(),s.getOperatingSystem(),s.getStatus(),s.getConnectionError(),s.getHostKeyFingerprint(),s.getCreatedAt());}
}
