package com.example.wgwarden.server;

import com.example.wgwarden.crypto.CryptoService;
import com.example.wgwarden.provisioning.WgInterface;
import com.example.wgwarden.provisioning.WgInterfaceRepository;
import com.example.wgwarden.ssh.SshClientService;
import com.example.wgwarden.ssh.SshCredentials;
import com.example.wgwarden.ssh.SshConnectionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ServerService {
    private static final Logger log = LoggerFactory.getLogger(ServerService.class);
    private static final Pattern INTERFACE_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,15}");
    private final ServerRepository repository; private final WgInterfaceRepository interfaces; private final CryptoService crypto; private final ObjectMapper mapper; private final SshClientService ssh;
    public ServerService(ServerRepository repository,WgInterfaceRepository interfaces,CryptoService crypto,SshClientService ssh,ObjectMapper mapper){this.repository=repository;this.interfaces=interfaces;this.crypto=crypto;this.mapper=mapper;this.ssh=ssh;}
    @Transactional(readOnly=true) public List<ServerResponse> list(UUID user){return repository.findAllByUserIdOrderByCreatedAtDesc(user).stream().map(ServerResponse::from).toList();}
    @Transactional(readOnly=true) public ServerResponse get(UUID user,UUID id){return ServerResponse.from(owned(user,id));}
    @Transactional(readOnly=true) public SshCredentials credentials(UUID user,UUID id){return decrypt(owned(user,id));}
    public ServerResponse create(UUID user,ServerRequest request){
        validateHost(request.host());
        validateCredentials(request.credentials());
        Server server=repository.save(new Server(user,request.name().trim(),request.host().trim(),request.sshPort(),request.sshUser().trim(),encrypt(request.credentials())));
        try { var result=ssh.probe(server.getHost(),server.getSshPort(),server.getSshUser(),toCredentials(request.credentials()),null); server.connectionResult(result.fingerprint(),result.operatingSystem()); }
        catch(RuntimeException e){server.markError(connectionError(e));}
        repository.save(server);
        return ServerResponse.from(server);
    }
    public ServerResponse update(UUID user,UUID id,ServerRequest request){
        validateHost(request.host());
        Server server=owned(user,id); byte[] encrypted=null;
        if(request.credentials()!=null){validateCredentials(request.credentials());encrypted=encrypt(request.credentials());}
        server.update(request.name().trim(),request.host().trim(),request.sshPort(),request.sshUser().trim(),encrypted);
        repository.save(server);
        SshCredentials credentials=request.credentials()==null?decrypt(server):toCredentials(request.credentials());
        // trustNewHostKey — явное действие пользователя после переустановки VPS.
        String pinned=Boolean.TRUE.equals(request.trustNewHostKey())?null:server.getHostKeyFingerprint();
        try {var result=ssh.probe(server.getHost(),server.getSshPort(),server.getSshUser(),credentials,pinned);server.connectionResult(result.fingerprint(),result.operatingSystem());}
        catch(RuntimeException e){server.markError(connectionError(e));}
        repository.save(server);
        return ServerResponse.from(server);
    }
    public void delete(UUID user,UUID id){
        Server server=owned(user,id);
        // Сначала отзываем VPN-доступ на самом VPS. Если это не удалось,
        // запись не удаляем, чтобы пользователь не считал доступ закрытым.
        removeWireGuard(server);
        repository.delete(server);
    }

    private void removeWireGuard(Server server){
        List<WgInterface> list=interfaces.findAllByServerIdOrderByName(server.getId());
        if(list.isEmpty())return;
        if(server.getHostKeyFingerprint()==null)throw new IllegalStateException("server host key is not pinned; refusing to delete without remote cleanup");
        SshCredentials credentials=decrypt(server);
        RuntimeException failure=null;
        for(WgInterface wg:list){
            if(!INTERFACE_NAME.matcher(wg.getName()).matches()){
                failure=new IllegalStateException("stored WireGuard interface name is invalid");
                log.warn("WireGuard cleanup failed for server {}: unsafe interface name",server.getId());
                break;
            }
            try{ssh.runSudoScript(server.getHost(),server.getSshPort(),server.getSshUser(),credentials,server.getHostKeyFingerprint(),teardownScript(wg.getName()));}
            catch(RuntimeException e){failure=e;log.warn("WireGuard cleanup failed for server {} interface {}",server.getId(),wg.getName());}
        }
        if(failure!=null)throw new SshConnectionException("could not revoke WireGuard access on the server; server was not deleted",failure);
    }

    private String teardownScript(String interfaceName){
        return """
                set -euo pipefail
                iface=%s
                conf=/etc/wireguard/$iface.conf
                if [ -f "$conf" ] && grep -qF '# Managed-by: wg-warden' "$conf"; then
                  systemctl disable --now "wg-quick@$iface" >/dev/null 2>&1 || true
                  rm -f "$conf"
                  rm -f /etc/sysctl.d/99-wg-warden.conf
                  if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q 'Status: active'; then
                    ufw delete allow 51821/udp >/dev/null 2>&1 || true
                  fi
                  sysctl --system >/dev/null 2>&1 || true
                fi
                """.formatted(interfaceName);
    }
    private Server owned(UUID user,UUID id){return repository.findByIdAndUserId(id,user).orElseThrow(ServerNotFoundException::new);}
    private void validateCredentials(ServerRequest.Credentials c){if(c==null||!c.valid())throw new IllegalArgumentException("provide exactly one of password or privateKey");}
    private void validateHost(String raw){
        String host=raw==null?"":raw.trim();
        if(host.isBlank()||host.length()>255||host.contains("/")||host.contains("\\")||host.chars().anyMatch(Character::isWhitespace)
                ||!Pattern.matches("^[A-Za-z0-9][A-Za-z0-9.:-]*[A-Za-z0-9]$|^[A-Za-z0-9]$",host))
            throw new IllegalArgumentException("host must be a valid hostname or IP address");
    }
    private byte[] encrypt(ServerRequest.Credentials c){try{return crypto.encrypt(mapper.writeValueAsBytes(c));}catch(Exception e){throw new IllegalStateException("could not encrypt SSH credentials",e);}}
    private SshCredentials decrypt(Server s){try{return mapper.readValue(new String(crypto.decrypt(s.getEncryptedCredentials()),StandardCharsets.UTF_8),SshCredentials.class);}catch(Exception e){throw new IllegalStateException("could not read SSH credentials",e);}}
    private SshCredentials toCredentials(ServerRequest.Credentials c){return new SshCredentials(c.password(),c.privateKey(),c.passphrase());}
    private String connectionError(RuntimeException error){return error instanceof SshConnectionException?error.getMessage():"SSH connection failed; check credentials, host key and server availability";}
}
