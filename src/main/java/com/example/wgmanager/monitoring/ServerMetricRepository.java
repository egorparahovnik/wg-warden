package com.example.wgmanager.monitoring;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.*;
public interface ServerMetricRepository extends JpaRepository<ServerMetric,UUID>{
    List<ServerMetric> findAllByServerIdAndTimestampBetweenOrderByTimestampAsc(UUID serverId,Instant from,Instant to);
}
