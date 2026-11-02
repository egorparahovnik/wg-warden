package com.example.wgwarden.peer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PeerRepository extends JpaRepository<Peer,UUID>{
    List<Peer> findAllByInterfaceIdOrderByCreatedAtDesc(UUID interfaceId);
    boolean existsByInterfaceIdAndAllowedIp(UUID interfaceId,String allowedIp);
}
