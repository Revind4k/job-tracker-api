package dev.erenyasar.jobtracker.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window rate limiter in Redis: one counter per client per minute.
 * INCR and EXPIRE run in one Lua script so concurrent requests cannot race between them.
 * If Redis is unreachable the filter fails open: a cache outage should not take the API down.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final RedisScript<List> SCRIPT = RedisScript.of("""
            local n = redis.call('INCR', KEYS[1])
            if n == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return {n, redis.call('TTL', KEYS[1])}
            """, List.class);

    private final StringRedisTemplate redis;
    private final int limit;
    private final boolean enabled;

    public RateLimitFilter(StringRedisTemplate redis, @Value("${app.rate-limit.requests-per-minute}") int limit,
                           @Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this.redis = redis;
        this.limit = limit;
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Disabled when the deployment has no Redis at all, rather than failing open per request.
        return !enabled || request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String key = "rl:" + clientId(req) + ":" + (System.currentTimeMillis() / 60_000);
        long count, ttl;
        try {
            List<?> r = redis.execute(SCRIPT, List.of(key), "60");
            count = ((Number) r.get(0)).longValue();
            ttl = ((Number) r.get(1)).longValue();
        } catch (RuntimeException e) {
            log.warn("rate limiter unavailable, allowing request: {}", e.toString());
            chain.doFilter(req, res);
            return;
        }
        res.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        res.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - count)));
        if (count > limit) {
            res.setStatus(429);
            res.setHeader("Retry-After", String.valueOf(Math.max(1, ttl)));
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"rate limit exceeded\",\"retryAfterSeconds\":" + Math.max(1, ttl) + "}");
            return;
        }
        chain.doFilter(req, res);
    }

    private static String clientId(HttpServletRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) return "u" + jwt.getToken().getSubject();
        return "ip" + req.getRemoteAddr();
    }
}
