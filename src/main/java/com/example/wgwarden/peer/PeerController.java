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
    public PeerResponse update(Authentication authentication,@PathVariable UUID peerId,@Valid @RequestBody PeerUpdateRequest request){return service.update(user(authentication),peerId,request);}

    @GetMapping("/api/peers/{peerId}/config")
    public PeerCreationResponse config(Authentication authentication,@PathVariable UUID peerId){return service.config(user(authentication),peerId);}

    @DeleteMapping("/api/peers/{peerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication,@PathVariable UUID peerId){service.delete(user(authentication),peerId);}

    private UUID user(Authentication authentication){return (UUID)authentication.getPrincipal();}
}
