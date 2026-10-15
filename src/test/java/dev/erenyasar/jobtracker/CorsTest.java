package dev.erenyasar.jobtracker;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.cors.allowed-origins=https://revind4k.github.io, http://localhost:5173")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsTest {
    @Autowired MockMvc mvc;

    @Test
    void preflightFromTheWebAppIsAllowedWithoutAToken() throws Exception {
        mvc.perform(options("/applications")
                        .header("Origin", "https://revind4k.github.io")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://revind4k.github.io"))
                .andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("PATCH")));
    }

    @Test
    void otherOriginsAreRejected() throws Exception {
        mvc.perform(options("/applications")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
