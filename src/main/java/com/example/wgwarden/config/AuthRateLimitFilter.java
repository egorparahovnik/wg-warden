package com.example.wgwarden.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

final class AuthRateLimitFilter extends OncePerRequestFilter {
    private record Window(long started, int count) {}
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (!request.getRequestURI().equals("/api/auth/login") && !request.getRequestURI().equals("/api/auth/register")) { chain.doFilter(request,response); return; }
        String key=request.getHeader("X-Real-IP");
        if(key==null||key.isBlank()) key=request.getRemoteAddr();
        long now=Instant.now().getEpochSecond();
        Window next=windows.compute(key,(k,w)->w==null||now-w.started()>=60?new Window(now,1):new Window(w.started(),w.count()+1));
        if(next.count()>10){response.setStatus(429); response.setHeader("Retry-After","60"); return;}
        chain.doFilter(request,response);
    }
}
