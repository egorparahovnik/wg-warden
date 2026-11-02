package com.example.wgwarden.server;

import com.example.wgwarden.crypto.CryptoService;
import com.example.wgwarden.ssh.SshClientService;
import com.example.wgwarden.ssh.SshCredentials;
import com.example.wgwarden.ssh.SshConnectionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ServerService {
    private final ServerRepository repository; private final CryptoService crypto; private final ObjectMapper mapper; private final SshClientService ssh;
    public ServerService(ServerRepository repository,CryptoService crypto,SshClientService ssh,ObjectMapper mapper){this.repository=repository;this.crypto=crypto;this.mapper=mapper;this.ssh=ssh;}
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
        try {var result=ssh.probe(server.getHost(),server.getSshPort(),server.getSshUser(),credentials,server.getHostKeyFingerprint());server.connectionResult(result.fingerprint(),result.operatingSystem());}
        catch(RuntimeException e){server.markError(connectionError(e));}
        repository.save(server);
        return ServerResponse.from(server);
    }
    @Transactional public void delete(UUID user,UUID id){repository.delete(owned(user,id));}
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
