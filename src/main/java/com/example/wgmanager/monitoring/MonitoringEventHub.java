package com.example.wgmanager.monitoring;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@Component
public class MonitoringEventHub {
    private final ConcurrentMap<UUID,Set<SseEmitter>> emitters=new ConcurrentHashMap<>();
    public SseEmitter subscribe(UUID userId){
        SseEmitter emitter=new SseEmitter(0L);Set<SseEmitter> clients=emitters.computeIfAbsent(userId,ignored->ConcurrentHashMap.newKeySet());clients.add(emitter);
        Runnable remove=()->{clients.remove(emitter);if(clients.isEmpty())emitters.remove(userId,clients);};
        emitter.onCompletion(remove);emitter.onTimeout(remove);emitter.onError(error->remove.run());return emitter;
    }
    public void publish(UUID userId,MonitorUpdate update){
        Set<SseEmitter> clients=emitters.get(userId);if(clients==null)return;
        for(SseEmitter client:clients)try{client.send(SseEmitter.event().name("monitor").data(update));}catch(IOException|IllegalStateException e){client.completeWithError(e);clients.remove(client);}
    }
    @Scheduled(fixedRate=15_000)
    public void heartbeat(){for(Set<SseEmitter> clients:emitters.values())for(SseEmitter client:clients)try{client.send(SseEmitter.event().comment("keepalive"));}catch(IOException|IllegalStateException e){client.completeWithError(e);clients.remove(client);}}
}
