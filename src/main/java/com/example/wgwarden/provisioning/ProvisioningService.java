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
import java.util.*;
import com.example.wgwarden.peer.Peer;
import com.example.wgwarden.peer.PeerRepository;

@Service
public class ProvisioningService {
    private static final String INTERFACE="wg-warden0";
    private static final int PORT=51821;
    private static final String SUBNET="10.67.0.0/24";
    private final ServerRepository servers; private final WgInterfaceRepository interfaces; private final PeerRepository peers; private final SshClientService ssh; private final CryptoService crypto; private final com.example.wgwarden.server.ServerService serverService;
    public ProvisioningService(ServerRepository servers,WgInterfaceRepository interfaces,PeerRepository peers,SshClientService ssh,CryptoService crypto,com.example.wgwarden.server.ServerService serverService){this.servers=servers;this.interfaces=interfaces;this.peers=peers;this.ssh=ssh;this.crypto=crypto;this.serverService=serverService;}

    public List<DiscoveredInterface> discover(UUID userId, UUID serverId) {
        Server server=servers.findByIdAndUserId(serverId,userId).orElseThrow(ServerNotFoundException::new); ensurePinned(server);
        String output=ssh.runSudoScript(server.getHost(),server.getSshPort(),server.getSshUser(),serverService.credentials(userId,serverId),server.getHostKeyFingerprint(),discoveryScript());
        Map<String,List<DiscoveredPeer>> found=new LinkedHashMap<>(); Map<String,String[]> metadata=new LinkedHashMap<>();
        for(String line:output.lines().toList()){String[] p=line.split("\\t",-1);if(p.length>=2&&p[0].equals("IF"))metadata.put(p[1],p);else if(p.length>=5&&p[0].equals("PEER"))found.computeIfAbsent(p[1],k->new ArrayList<>()).add(new DiscoveredPeer(p[4],p[2],p[3]));}
        return metadata.values().stream().map(p->new DiscoveredInterface(p[1],parseInt(p[2]),p[3],p[4],found.getOrDefault(p[1],List.of()))).toList();
    }

    @Transactional
    public List<DiscoveredInterface> importExisting(UUID userId,UUID serverId,ImportRequest request){
        if(request==null||request.interfaceName()==null||!request.interfaceName().matches("[A-Za-z0-9_.-]{1,15}"))throw new IllegalArgumentException("invalid WireGuard interface name");
        Server server=servers.findByIdAndUserId(serverId,userId).orElseThrow(ServerNotFoundException::new);ensurePinned(server);
        DiscoveredInterface remote=discover(userId,serverId).stream().filter(i->i.name().equals(request.interfaceName())).findFirst().orElseThrow(()->new IllegalArgumentException("interface was not found on the server"));
        String privateKey=ssh.runSudoScript(server.getHost(),server.getSshPort(),server.getSshUser(),serverService.credentials(userId,serverId),server.getHostKeyFingerprint(),"wg show "+remote.name()+" private-key").trim();
        if(privateKey.isBlank()||privateKey.contains(" "))throw new IllegalStateException("could not read the interface private key");
        WgInterface local=interfaces.findByServerIdAndName(serverId,remote.name()).orElseGet(()->interfaces.save(new WgInterface(serverId,remote.name(),remote.listenPort(),remote.subnet(),remote.publicKey(),crypto.encrypt(privateKey.getBytes(StandardCharsets.UTF_8)))));
        Set<String> selected=request.peerPublicKeys()==null?Set.of():new HashSet<>(request.peerPublicKeys());
        for(DiscoveredPeer p:remote.peers())if(selected.contains(p.publicKey())&&p.allowedIp()!=null&&!p.allowedIp().contains(",")&&peers.findByInterfaceIdAndPublicKey(local.getId(),p.publicKey()).isEmpty()){
            Peer peer=new Peer(local.getId(),p.name(),p.publicKey(),p.allowedIp()); String clientKey=request.peerPrivateKeys()==null?null:request.peerPrivateKeys().get(p.publicKey());
            if(clientKey!=null&&!clientKey.isBlank())peer.setEncryptedPrivateKey(crypto.encrypt(clientKey.trim().getBytes(StandardCharsets.UTF_8))); peers.save(peer);
        }
        return discover(userId,serverId);
    }

    private void ensurePinned(Server server){if(server.getHostKeyFingerprint()==null)throw new IllegalStateException("server host key is not pinned; reconnect and verify the server first");}
    private int parseInt(String value){try{return Integer.parseInt(value);}catch(NumberFormatException e){return 0;}}
    private String discoveryScript(){return "set -euo pipefail\nfor i in $(wg show interfaces 2>/dev/null); do port=$(wg show $i listen-port); pub=$(wg show $i public-key); subnet=$(ip -o -4 addr show dev $i | awk 'NR==1{print $4}'); printf 'IF\\t%s\\t%s\\t%s\\t%s\\n' \"$i\" \"$port\" \"${subnet:-0.0.0.0/0}\" \"$pub\"; wg show $i dump | tail -n +2 | awk -v iface=\"$i\" 'NF>=4 {split($4,a,\",\"); printf \"PEER\\t%s\\t%s\\t%s\\tpeer-%s\\n\",iface,$1,a[1],substr($1,1,8)}'; done";}

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
                : updateExisting(existing, publicKey, privateKey);
        return ProvisioningResponse.ready(result);
    }
    private WgInterface updateExisting(WgInterface existing, String publicKey, String privateKey) {
        existing.updateKeys(publicKey, crypto.encrypt(privateKey.getBytes(StandardCharsets.UTF_8)));
        return interfaces.save(existing);
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
            peers_tmp=$(mktemp)
            trap 'rm -f "$peers_tmp"' EXIT
            if [ -e "$conf" ]; then
              grep -Eq '^# Managed-by: (wg-warden|wg-manager)$' "$conf" || { echo 'wg-warden0 configuration already exists and is not managed by wg-warden' >&2; exit 20; }
              private_key=$(awk -F ' = ' '$1 == "PrivateKey" {print $2; exit}' "$conf")
              [ -n "$private_key" ] || { echo 'managed wg-warden0 configuration has no private key' >&2; exit 21; }
              awk '/^\\[Peer\\][[:space:]]*$/{found=1} found{print}' "$conf" > "$peers_tmp"
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
            cat "$peers_tmp" >> "$conf"
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
