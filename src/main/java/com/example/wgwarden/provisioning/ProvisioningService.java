package com.example.wgwarden.provisioning;

import com.example.wgwarden.crypto.CryptoService;
import com.example.wgwarden.server.Server;
import com.example.wgwarden.server.ServerNotFoundException;
import com.example.wgwarden.server.ServerRepository;
import com.example.wgwarden.ssh.SshClientService;
import com.example.wgwarden.ssh.SshCredentials;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class ProvisioningService {
    private static final String INTERFACE="wg-warden0";
    private static final int PORT=51821;
    private static final String SUBNET="10.67.0.0/24";
    private final ServerRepository servers; private final WgInterfaceRepository interfaces; private final SshClientService ssh; private final CryptoService crypto; private final com.example.wgwarden.server.ServerService serverService;
    public ProvisioningService(ServerRepository servers,WgInterfaceRepository interfaces,SshClientService ssh,CryptoService crypto,com.example.wgwarden.server.ServerService serverService){this.servers=servers;this.interfaces=interfaces;this.ssh=ssh;this.crypto=crypto;this.serverService=serverService;}

    @Transactional
    public ProvisioningResponse provision(UUID userId,UUID serverId){
        Server server=servers.findByIdAndUserId(serverId,userId).orElseThrow(ServerNotFoundException::new);
        if(server.getHostKeyFingerprint()==null)throw new IllegalStateException("server host key is not pinned; reconnect and verify the server first");
        if(!"Ubuntu".equals(server.getOperatingSystem())&&!"Debian".equals(server.getOperatingSystem()))throw new IllegalStateException("only Ubuntu and Debian are supported");
        WgInterface existing=interfaces.findByServerIdAndName(serverId,INTERFACE).orElse(null);
        String script=setupScript();
        String output=ssh.runSudoScript(server.getHost(),server.getSshPort(),server.getSshUser(),serverService.credentials(userId,serverId),server.getHostKeyFingerprint(),script);
        String privateKey=value(output,"WG_PRIVATE");String publicKey=value(output,"WG_PUBLIC");
        if(privateKey.isBlank()||publicKey.isBlank())throw new IllegalStateException("remote provisioning returned invalid WireGuard key material");
        WgInterface result=existing==null
                ? interfaces.save(new WgInterface(serverId,INTERFACE,PORT,SUBNET,publicKey,crypto.encrypt(privateKey.getBytes(StandardCharsets.UTF_8))))
                : existing;
        return ProvisioningResponse.ready(result);
    }
    private String value(String output,String key){return output.lines().filter(l->l.startsWith(key+"=")).map(l->l.substring(key.length()+1).trim()).findFirst().orElse("");}
    private String setupScript(){return """
            set -euo pipefail
            export DEBIAN_FRONTEND=noninteractive
            if ! command -v wg >/dev/null 2>&1 || ! command -v wg-quick >/dev/null 2>&1; then
              apt-get update
              apt-get install -y wireguard iptables
            fi
            install -d -m 700 /etc/wireguard
            conf=/etc/wireguard/wg-warden0.conf
            if [ -e "$conf" ]; then
              grep -Eq '^# Managed-by: (wg-warden|wg-manager)$' "$conf" || { echo 'wg-warden0 configuration already exists and is not managed by wg-warden' >&2; exit 20; }
              private_key=$(awk -F ' = ' '$1 == "PrivateKey" {print $2; exit}' "$conf")
              [ -n "$private_key" ] || { echo 'managed wg-warden0 configuration has no private key' >&2; exit 21; }
            else
              private_key=$(wg genkey)
            fi
            public_key=$(printf '%s' "$private_key" | wg pubkey)
            egress=$(ip -o route show default | awk 'NR == 1 {print $5}')
            [[ "$egress" =~ ^[[:alnum:]_.:-]+$ ]] || { echo 'could not determine default network interface' >&2; exit 22; }
            umask 077
            cat > "$conf" <<EOF
            # Managed-by: wg-warden
            [Interface]
            Address = 10.67.0.1/24
            ListenPort = 51821
            PrivateKey = $private_key
            PostUp = iptables -t nat -C POSTROUTING -s 10.67.0.0/24 -o $egress -j MASQUERADE 2>/dev/null || iptables -t nat -A POSTROUTING -s 10.67.0.0/24 -o $egress -j MASQUERADE
            PostDown = iptables -t nat -D POSTROUTING -s 10.67.0.0/24 -o $egress -j MASQUERADE 2>/dev/null || true
            EOF
            chmod 600 "$conf"
            printf '%s\\n' 'net.ipv4.ip_forward=1' > /etc/sysctl.d/99-wg-warden.conf
            sysctl -p /etc/sysctl.d/99-wg-warden.conf >/dev/null
            systemctl enable wg-quick@wg-warden0 >/dev/null
            if ! systemctl is-active --quiet wg-quick@wg-warden0; then
              systemctl start wg-quick@wg-warden0
            fi
            printf 'WG_PRIVATE=%s\\nWG_PUBLIC=%s\\n' "$private_key" "$public_key"
            """;
    }
}
