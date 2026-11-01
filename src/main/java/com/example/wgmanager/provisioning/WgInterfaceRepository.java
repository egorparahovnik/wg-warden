package com.example.wgmanager.provisioning;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface WgInterfaceRepository extends JpaRepository<WgInterface,UUID>{Optional<WgInterface> findByServerIdAndName(UUID serverId,String name);}
