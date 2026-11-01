package com.example.wgmanager.provisioning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface WgInterfaceRepository extends JpaRepository<WgInterface,UUID>{
 Optional<WgInterface> findByServerIdAndName(UUID serverId,String name);
 Optional<WgInterface> findByIdAndServerId(UUID id,UUID serverId);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select w from WgInterface w where w.id=:id and w.serverId=:serverId")
 Optional<WgInterface> lockByIdAndServerId(@Param("id") UUID id,@Param("serverId") UUID serverId);
}
