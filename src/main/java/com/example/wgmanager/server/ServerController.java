package com.example.wgmanager.server;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
@RestController
@RequestMapping("/api/servers")
public class ServerController {
 private final ServerService service;
 public ServerController(ServerService service){this.service=service;}
 @GetMapping public List<ServerResponse> list(Authentication a){return service.list(user(a));}
 @GetMapping("/{id}") public ServerResponse get(Authentication a,@PathVariable UUID id){return service.get(user(a),id);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public ServerResponse create(Authentication a,@Valid @RequestBody ServerRequest r){return service.create(user(a),r);}
 @PutMapping("/{id}") public ServerResponse update(Authentication a,@PathVariable UUID id,@Valid @RequestBody ServerRequest r){return service.update(user(a),id,r);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(Authentication a,@PathVariable UUID id){service.delete(user(a),id);}
 private UUID user(Authentication a){return (UUID)a.getPrincipal();}
}
