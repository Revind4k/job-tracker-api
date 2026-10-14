package dev.erenyasar.jobtracker;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.rate-limit.requests-per-minute=5")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitTest {
    @Autowired MockMvc mvc;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void clean() {
        var keys = redis.keys("rl:*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
    }

    @Test
    void sixthRequestInAMinuteGets429WithRetryAfter() throws Exception {
        for (int i = 0; i < 5; i++)
            mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"x@example.com\",\"password\":\"whatever-pass\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(4 - i)));
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@example.com\",\"password\":\"whatever-pass\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }
}
