package com.example.wgmanager.peer;

import com.example.wgmanager.provisioning.WgInterface;
import com.example.wgmanager.provisioning.WgInterfaceRepository;
import com.example.wgmanager.server.Server;
import com.example.wgmanager.server.ServerNotFoundException;
import com.example.wgmanager.server.ServerRepository;
import com.example.wgmanager.server.ServerService;
import com.example.wgmanager.ssh.SshClientService;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.util.*;

@Service
public class PeerService {
    private final PeerRepository peers; private final WgInterfaceRepository interfaces; private final ServerRepository servers;
    private final SshClientService ssh; private final ServerService serverService; private final SecureRandom random=new SecureRandom();
    public PeerService(PeerRepository peers,WgInterfaceRepository interfaces,ServerRepository servers,SshClientService ssh,ServerService serverService){this.peers=peers;this.interfaces=interfaces;this.servers=servers;this.ssh=ssh;this.serverService=serverService;}

    @Transactional(readOnly=true)
    public List<PeerResponse> list(UUID userId,UUID serverId){
        Server server=servers.findByIdAndUserId(serverId,userId).orElseThrow(ServerNotFoundException::new);
        WgInterface wg=interfaces.findByServerIdAndName(server.getId(),"wg0").orElse(null);
        return wg==null?List.of():peers.findAllByInterfaceIdOrderByCreatedAtDesc(wg.getId()).stream().map(PeerResponse::from).toList();
    }

    @Transactional
    public PeerCreationResponse create(UUID userId,UUID serverId,PeerRequest request){
        Server server=servers.findByIdAndUserId(serverId,userId).orElseThrow(ServerNotFoundException::new);
        WgInterface wg=interfaces.findByServerIdAndName(serverId,"wg0").orElseThrow(()->new IllegalStateException("provision WireGuard on this server first"));
        WgInterface locked=interfaces.lockByIdAndServerId(wg.getId(),serverId).orElseThrow(()->new IllegalStateException("WireGuard interface not found"));
        ensurePinned(server);
        String ip=nextAddress(locked);
        KeyPair pair=generateKeyPair();
        String name=request.name().trim();
        ssh.runSudoScript(server.getHost(),server.getSshPort(),server.getSshUser(),serverService.credentials(userId,serverId),server.getHostKeyFingerprint(),addPeerScript(pair.publicKey(),ip));
        Peer peer=peers.save(new Peer(locked.getId(),name,pair.publicKey(),ip+"/32"));
        String config=clientConfig(server,locked,ip,pair.privateKey());
        return new PeerCreationResponse(PeerResponse.from(peer),config,qr(config));
    }

    @Transactional
    public PeerResponse setEnabled(UUID userId,UUID peerId,boolean enabled){
        Peer peer=ownedPeer(userId,peerId);
        interfaces.lockByIdAndServerId(peer.getInterfaceId(),serverIdFor(userId,peer)).orElseThrow(PeerNotFoundException::new);
        if(peer.isEnabled()!=enabled){
            Server server=servers.findByIdAndUserId(serverIdFor(userId,peer),userId).orElseThrow(ServerNotFoundException::new);
            ensurePinned(server);
            ssh.runSudoScript(server.getHost(),server.getSshPort(),server.getSshUser(),serverService.credentials(userId,server.getId()),server.getHostKeyFingerprint(),enabled?addPeerScript(peer.getPublicKey(),address(peer.getAllowedIp())):removePeerScript(peer.getPublicKey()));
            peer.setEnabled(enabled);
        }
        return PeerResponse.from(peer);
    }

    @Transactional
    public void delete(UUID userId,UUID peerId){
        Peer peer=ownedPeer(userId,peerId); UUID serverId=serverIdFor(userId,peer);
        interfaces.lockByIdAndServerId(peer.getInterfaceId(),serverId).orElseThrow(PeerNotFoundException::new);
        Server server=servers.findByIdAndUserId(serverId,userId).orElseThrow(ServerNotFoundException::new);
        ensurePinned(server);
        ssh.runSudoScript(server.getHost(),server.getSshPort(),server.getSshUser(),serverService.credentials(userId,serverId),server.getHostKeyFingerprint(),removePeerScript(peer.getPublicKey()));
        peers.delete(peer);
    }

    private Peer ownedPeer(UUID userId,UUID peerId){
        Peer peer=peers.findById(peerId).orElseThrow(PeerNotFoundException::new);
        UUID serverId=interfaces.findById(peer.getInterfaceId()).map(WgInterface::getServerId).orElseThrow(PeerNotFoundException::new);
        if(servers.findByIdAndUserId(serverId,userId).isEmpty())throw new PeerNotFoundException();
        return peer;
    }
    private UUID serverIdFor(UUID userId,Peer peer){
        UUID serverId=interfaces.findById(peer.getInterfaceId()).map(WgInterface::getServerId).orElseThrow(PeerNotFoundException::new);
        if(servers.findByIdAndUserId(serverId,userId).isEmpty())throw new PeerNotFoundException();
        return serverId;
    }
    private void ensurePinned(Server server){if(server.getHostKeyFingerprint()==null)throw new IllegalStateException("server SSH host key is not pinned");}

    private String nextAddress(WgInterface wg){
        try{
            String[] parts=wg.getSubnet().split("/");int prefix=Integer.parseInt(parts[1]);
            byte[] bytes=InetAddress.getByName(parts[0]).getAddress();
            if(bytes.length!=4||prefix<16||prefix>30)throw new IllegalStateException("only IPv4 subnets from /16 through /30 are supported");
            long address=Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(bytes).getInt());
            long mask=(0xffffffffL << (32-prefix))&0xffffffffL;long network=address&mask;long broadcast=network|(~mask&0xffffffffL);
            Set<Long> used=new HashSet<>();
            for(Peer peer:peers.findAllByInterfaceIdOrderByCreatedAtDesc(wg.getId()))used.add(ipv4Long(address(peer.getAllowedIp())));
            long gateway=network+1;
            for(long candidate=network+2;candidate<broadcast;candidate++)if(candidate!=gateway&&!used.contains(candidate))return ipv4String(candidate);
            throw new IllegalStateException("WireGuard subnet has no available client addresses");
        }catch(IllegalArgumentException e){throw new IllegalStateException("invalid WireGuard subnet",e);}catch(java.net.UnknownHostException e){throw new IllegalStateException("invalid WireGuard subnet",e);}
    }
    private long ipv4Long(String value){try{return Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(InetAddress.getByName(address(value)).getAddress()).getInt());}catch(Exception e){throw new IllegalStateException("stored peer IP is invalid",e);}}
    private String address(String value){int slash=value.indexOf('/');return slash<0?value:value.substring(0,slash);}
    private String ipv4String(long ip){return ((ip>>>24)&255)+"."+((ip>>>16)&255)+"."+((ip>>>8)&255)+"."+(ip&255);}
    private record KeyPair(String privateKey,String publicKey){}
    private KeyPair generateKeyPair(){byte[] privateBytes=new X25519PrivateKeyParameters(random).getEncoded();byte[] publicBytes=new X25519PrivateKeyParameters(privateBytes,0).generatePublicKey().getEncoded();return new KeyPair(Base64.getEncoder().encodeToString(privateBytes),Base64.getEncoder().encodeToString(publicBytes));}
    private String clientConfig(Server server,WgInterface wg,String ip,String privateKey){
        String endpoint=server.getHost().contains(":")&&!server.getHost().startsWith("[")?"["+server.getHost()+"]":server.getHost();
        return "[Interface]\nPrivateKey = "+privateKey+"\nAddress = "+ip+"/32\nDNS = 1.1.1.1\n\n[Peer]\nPublicKey = "+wg.getPublicKey()+"\nEndpoint = "+endpoint+":"+wg.getListenPort()+"\nAllowedIPs = 0.0.0.0/0\nPersistentKeepalive = 25\n";
    }
    private String qr(String text){
        try(ByteArrayOutputStream out=new ByteArrayOutputStream()){
            MatrixToImageWriter.writeToStream(new QRCodeWriter().encode(text,BarcodeFormat.QR_CODE,512,512),"PNG",out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        }catch(Exception e){throw new IllegalStateException("could not create peer QR code",e);}
    }
    private String addPeerScript(String publicKey,String ip){
        return "set -euo pipefail\nexec 9>/run/lock/wg-manager-wg0.lock\nflock -x 9\nconf=/etc/wireguard/wg0.conf\n"+
                "grep -qF 'PublicKey = "+publicKey+"' \"$conf\" || cat >> \"$conf\" <<'WG_PEER'\n[Peer]\nPublicKey = "+publicKey+"\nAllowedIPs = "+ip+"/32\nWG_PEER\n"+
                "wg syncconf wg0 <(wg-quick strip wg0)\n";
    }
    private String removePeerScript(String publicKey){
        return "set -euo pipefail\nexec 9>/run/lock/wg-manager-wg0.lock\nflock -x 9\nconf=/etc/wireguard/wg0.conf\ntmp=$(mktemp)\n"+
                "awk -v key='"+publicKey+"' 'function flush(){if(!drop)for(i=1;i<=n;i++)print lines[i];delete lines;n=0;drop=0} " +
                "/^\\[Peer\\][[:space:]]*$/ {flush();peer=1;lines[++n]=$0;next} peer {lines[++n]=$0;if($1==\"PublicKey\"&&$2==\"=\"&&$3==key)drop=1;next} {print} END{flush()}' \"$conf\" > \"$tmp\"\n"+
                "install -m 600 \"$tmp\" \"$conf\"; rm -f \"$tmp\"\nwg syncconf wg0 <(wg-quick strip wg0)\n";
    }
}
