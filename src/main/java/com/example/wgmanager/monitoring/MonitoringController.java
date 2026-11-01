package com.example.wgmanager.monitoring;

import com.example.wgmanager.peer.PeerNotFoundException;
import com.example.wgmanager.peer.PeerRepository;
import com.example.wgmanager.provisioning.WgInterfaceRepository;
import com.example.wgmanager.server.ServerNotFoundException;
import com.example.wgmanager.server.ServerRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api")
public class MonitoringController {
    private final ServerRepository servers;private final PeerRepository peers;private final WgInterfaceRepository interfaces;
    private final ServerMetricRepository serverMetrics;private final PeerMetricRepository peerMetrics;private final MonitoringEventHub events;
    public MonitoringController(ServerRepository servers,PeerRepository peers,WgInterfaceRepository interfaces,ServerMetricRepository serverMetrics,PeerMetricRepository peerMetrics,MonitoringEventHub events){this.servers=servers;this.peers=peers;this.interfaces=interfaces;this.serverMetrics=serverMetrics;this.peerMetrics=peerMetrics;this.events=events;}

    @GetMapping(value="/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication){return events.subscribe(userId(authentication));}

    @GetMapping("/servers/{serverId}/metrics")
    public List<ServerMetricResponse> serverHistory(Authentication authentication,@PathVariable UUID serverId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant to){
        requireServerOwner(authentication,serverId);Instant[] range=range(from,to);
        return serverMetrics.findAllByServerIdAndTimestampBetweenOrderByTimestampAsc(serverId,range[0],range[1]).stream().map(ServerMetricResponse::from).toList();
    }

    @GetMapping("/peers/{peerId}/metrics")
    public List<PeerMetricResponse> peerHistory(Authentication authentication,@PathVariable UUID peerId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant to){
        var peer=peers.findById(peerId).orElseThrow(PeerNotFoundException::new);
        UUID serverId=interfaces.findById(peer.getInterfaceId()).map(i->i.getServerId()).orElseThrow(PeerNotFoundException::new);
        requireServerOwner(authentication,serverId);Instant[] range=range(from,to);
        return peerMetrics.findAllByPeerIdAndTimestampBetweenOrderByTimestampAsc(peerId,range[0],range[1]).stream().map(PeerMetricResponse::from).toList();
    }

    private Instant[] range(Instant from,Instant to){
        Instant end=to==null?Instant.now():to;Instant start=from==null?end.minusSeconds(86_400):from;
        if(start.isAfter(end))throw new IllegalArgumentException("from must be before to");
        if(start.isBefore(end.minusSeconds(7*86_400L)))throw new IllegalArgumentException("metric history is limited to seven days");
        return new Instant[]{start,end};
    }
    private void requireServerOwner(Authentication authentication,UUID serverId){if(servers.findByIdAndUserId(serverId,userId(authentication)).isEmpty())throw new ServerNotFoundException();}
    private UUID userId(Authentication authentication){return (UUID)authentication.getPrincipal();}
}
