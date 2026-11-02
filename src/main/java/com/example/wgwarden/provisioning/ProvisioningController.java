package com.example.wgwarden.provisioning;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
@RequestMapping("/api/servers/{serverId}/provisioning")
public class ProvisioningController {
 private final ProvisioningService service;
 public ProvisioningController(ProvisioningService service){this.service=service;}
 @PostMapping("/wireguard") public ProvisioningResponse provision(Authentication a,@PathVariable UUID serverId){return service.provision((UUID)a.getPrincipal(),serverId);}
}
