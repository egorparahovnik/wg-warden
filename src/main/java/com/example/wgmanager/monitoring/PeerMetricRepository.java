package com.example.wgmanager.monitoring;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.*;
public interface PeerMetricRepository extends JpaRepository<PeerMetric,UUID>{
    List<PeerMetric> findAllByPeerIdAndTimestampBetweenOrderByTimestampAsc(UUID peerId,Instant from,Instant to);
    Optional<PeerMetric> findFirstByPeerIdOrderByTimestampDesc(UUID peerId);
}
