package com.example.wgmanager.ssh;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Service
public class SshClientService {
    public record ProbeResult(String fingerprint,String operatingSystem) {}
    public ProbeResult probe(String host,int port,String username,SshCredentials credentials,String pinnedFingerprint) {
        final String[] observed={null};
        try(SshClient client=SshClient.setUpDefaultClient()) {
            client.setServerKeyVerifier((session,address,key)->{
                final String fp;
                try {
                    fp=fingerprint(key);
                } catch (Exception e) {
                    return false;
                }
                observed[0]=fp;
                return pinnedFingerprint==null || pinnedFingerprint.equals(fp);
            });
            client.start();
            try(ClientSession session=client.connect(username,host,port).verify(10,TimeUnit.SECONDS).getSession()) {
                if(credentials.privateKey()!=null && !credentials.privateKey().isBlank()) {
                    byte[] key=credentials.privateKey().getBytes(StandardCharsets.UTF_8);
                    var identities=SecurityUtils.loadKeyPairIdentities(null, () -> "key", new ByteArrayInputStream(key),
                            credentials.passphrase()==null ? FilePasswordProvider.EMPTY : FilePasswordProvider.of(credentials.passphrase()));
                    identities.forEach(session::addPublicKeyIdentity);
                } else session.addPasswordIdentity(credentials.password());
                session.auth().verify(15,TimeUnit.SECONDS);
                String release=execute(session,"cat /etc/os-release");
                String id=release.lines().filter(line->line.startsWith("ID=")).map(line->line.substring(3).replace("\"", "").trim()).findFirst().orElse("");
                if(!id.equals("ubuntu")&&!id.equals("debian")) throw new IllegalStateException("unsupported operating system");
                return new ProbeResult(observed[0],id.substring(0,1).toUpperCase()+id.substring(1));
            }
        } catch (Exception e) { throw new SshConnectionException("SSH connection or OS detection failed",e); }
    }
    private String execute(ClientSession session,String command) throws IOException {
        try(var channel=session.createExecChannel(command); var out=new ByteArrayOutputStream()) {
            channel.setOut(out); channel.open().verify(10,TimeUnit.SECONDS); channel.waitFor(java.util.EnumSet.of(org.apache.sshd.client.channel.ClientChannelEvent.CLOSED),TimeUnit.SECONDS.toMillis(15));
            if(channel.getExitStatus()==null||channel.getExitStatus()!=0) throw new IOException("remote command failed");
            return out.toString(StandardCharsets.UTF_8);
        }
    }
    private String fingerprint(PublicKey key) throws Exception { return "SHA256:"+Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(key.getEncoded())); }
}
