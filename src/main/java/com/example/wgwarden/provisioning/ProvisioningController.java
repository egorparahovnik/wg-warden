package com.example.wgwarden.provisioning;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.List;
@RestController
@RequestMapping("/api/servers/{serverId}/provisioning")
public class ProvisioningController {
 private final ProvisioningService service;
 public ProvisioningController(ProvisioningService service){this.service=service;}
 @PostMapping("/wireguard") public ProvisioningResponse provision(Authentication a,@PathVariable UUID serverId){return service.provision((UUID)a.getPrincipal(),serverId);}
 @GetMapping("/discover") public List<DiscoveredInterface> discover(Authentication a,@PathVariable UUID serverId){return service.discover((UUID)a.getPrincipal(),serverId);}
 @PostMapping("/import") public List<DiscoveredInterface> importExisting(Authentication a,@PathVariable UUID serverId,@RequestBody ImportRequest request){return service.importExisting((UUID)a.getPrincipal(),serverId,request);}
}
