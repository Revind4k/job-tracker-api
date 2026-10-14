package dev.erenyasar.jobtracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiFlowTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CacheManager caches;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE users RESTART IDENTITY CASCADE");
        caches.getCacheNames().forEach(n -> caches.getCache(n).clear());
    }

    String register(String email) throws Exception {
        String body = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    long create(String token, String company, String status) throws Exception {
        String body = mvc.perform(post("/applications").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"" + company + "\",\"role\":\"SWE\",\"status\":\"" + status + "\",\"appliedOn\":\"2026-10-01\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void fullCrudFlowWithStats() throws Exception {
        String t = register("a@example.com");
        long id = create(t, "Acme", "APPLIED");
        create(t, "Globex", "INTERVIEW");

        mvc.perform(get("/applications").header("Authorization", t))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2));
        mvc.perform(patch("/applications/" + id).header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OFFER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OFFER"))
                .andExpect(jsonPath("$.company").value("Acme"));
        mvc.perform(get("/applications/stats").header("Authorization", t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.byStatus.OFFER").value(1))
                .andExpect(jsonPath("$.responseRate").value(1.0));
        mvc.perform(delete("/applications/" + id).header("Authorization", t)).andExpect(status().isNoContent());
        mvc.perform(get("/applications/" + id).header("Authorization", t)).andExpect(status().isNotFound());
    }

    @Test
    void usersCannotSeeEachOthersData() throws Exception {
        String a = register("a@example.com");
        String b = register("b@example.com");
        long id = create(a, "Acme", "APPLIED");
        mvc.perform(get("/applications/" + id).header("Authorization", b)).andExpect(status().isNotFound());
        mvc.perform(delete("/applications/" + id).header("Authorization", b)).andExpect(status().isNotFound());
        mvc.perform(get("/applications").header("Authorization", b)).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void rejectsMissingOrForgedTokens() throws Exception {
        mvc.perform(get("/applications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/applications").header("Authorization", "Bearer not.a.jwt")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginDoesNotRevealWhetherEmailExists() throws Exception {
        register("a@example.com");
        String wrongPassword = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknownEmail = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(wrongPassword).isEqualTo(unknownEmail);
    }

    @Test
    void validationErrorsNameTheField() throws Exception {
        String t = register("a@example.com");
        mvc.perform(post("/applications").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"\",\"role\":\"SWE\",\"appliedOn\":\"2099-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.company").exists())
                .andExpect(jsonPath("$.fields.appliedOn").exists());
    }

    @Test
    void statsAreCachedAndEvictedOnWrite() throws Exception {
        String t = register("a@example.com");
        create(t, "Acme", "APPLIED");
        mvc.perform(get("/applications/stats").header("Authorization", t)).andExpect(jsonPath("$.total").value(1));
        assertThat(caches.getCache("stats").get(1L)).isNotNull();

        create(t, "Globex", "APPLIED"); // a write must evict, or the next read would be stale
        assertThat(caches.getCache("stats").get(1L)).isNull();
        mvc.perform(get("/applications/stats").header("Authorization", t)).andExpect(jsonPath("$.total").value(2));
    }
}
