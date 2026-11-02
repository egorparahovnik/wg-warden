package com.example.wgwarden.peer;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
public class PeerController {
    private final PeerService service;
    public PeerController(PeerService service){this.service=service;}

    @GetMapping("/api/servers/{serverId}/peers")
    public List<PeerResponse> list(Authentication authentication,@PathVariable UUID serverId){return service.list(user(authentication),serverId);}

    @PostMapping("/api/servers/{serverId}/peers")
    @ResponseStatus(HttpStatus.CREATED)
    public PeerCreationResponse create(Authentication authentication,@PathVariable UUID serverId,@Valid @RequestBody PeerRequest request){return service.create(user(authentication),serverId,request);}

    @PatchMapping("/api/peers/{peerId}")
    public PeerResponse setEnabled(Authentication authentication,@PathVariable UUID peerId,@Valid @RequestBody PeerEnabledRequest request){return service.setEnabled(user(authentication),peerId,request.enabled());}

    @DeleteMapping("/api/peers/{peerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication,@PathVariable UUID peerId){service.delete(user(authentication),peerId);}

    private UUID user(Authentication authentication){return (UUID)authentication.getPrincipal();}
}
