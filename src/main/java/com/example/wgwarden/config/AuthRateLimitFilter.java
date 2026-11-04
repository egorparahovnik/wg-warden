package com.example.wgwarden.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final int LIMIT = 10;
    private static final long WINDOW_SECONDS = 60;

    private record Window(long started, int count) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final ClientIpResolver clientIp;

    public AuthRateLimitFilter(ClientIpResolver clientIp) {
        this.clientIp = clientIp;
    }

    /** Сбрасывает счётчики: нужен тестам, чтобы окна разных тестов не складывались. */
    public void clear() {
        windows.clear();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.equals("/api/auth/login") && !path.equals("/api/auth/register")) {
            chain.doFilter(request, response);
            return;
        }
        String key = clientIp.resolve(request);
        long now = Instant.now().getEpochSecond();
        Window next = windows.compute(key, (ignored, current) ->
                current == null || now - current.started() >= WINDOW_SECONDS
                        ? new Window(now, 1)
                        : new Window(current.started(), current.count() + 1));
        if (next.count() > LIMIT) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(WINDOW_SECONDS));
            return;
        }
        chain.doFilter(request, response);
    }

    /** Не даём карте окон расти бесконечно при сканировании с разных адресов. */
    @Scheduled(fixedRate = 60_000)
    public void removeExpiredWindows() {
        long now = Instant.now().getEpochSecond();
        windows.entrySet().removeIf(entry -> now - entry.getValue().started() >= WINDOW_SECONDS);
    }
}
