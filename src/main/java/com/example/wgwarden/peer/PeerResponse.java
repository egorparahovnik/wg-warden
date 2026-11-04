package com.example.wgwarden.peer;
import java.time.Instant;
import java.util.UUID;
public record PeerResponse(UUID id,String name,String publicKey,String allowedIp,boolean enabled,Instant createdAt,Long trafficLimitBytes,Instant expiresAt,boolean configAvailable){
 static PeerResponse from(Peer p){return new PeerResponse(p.getId(),p.getName(),p.getPublicKey(),p.getAllowedIp(),p.isEnabled(),p.getCreatedAt(),p.getTrafficLimitBytes(),p.getExpiresAt(),p.getEncryptedPrivateKey()!=null);}
}
