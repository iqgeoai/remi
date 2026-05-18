package com.remi.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remi.user.api.MockMailServiceTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
@Import(MockMailServiceTestConfig.class)
class DeviceTokenControllerTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;
    @Autowired DeviceTokenRepository repo;

    @BeforeEach
    void reset() {
        MockMailServiceTestConfig.SENT.clear();
        jdbc.execute("TRUNCATE device_tokens, game_players, games, refresh_tokens, verification_tokens, password_reset_tokens, users CASCADE");
    }

    private String registerVerifyLogin(String email, String username) throws Exception {
        String regBody = String.format(
            "{\"email\":\"%s\",\"username\":\"%s\",\"password\":\"passwordxx\"}", email, username);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(regBody))
            .andExpect(status().isCreated());
        var token = MockMailServiceTestConfig.SENT.get(MockMailServiceTestConfig.SENT.size() - 1).token();
        String verifyBody = String.format("{\"token\":\"%s\"}", token);
        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content(verifyBody))
            .andExpect(status().isNoContent());
        String loginBody = String.format(
            "{\"emailOrUsername\":\"%s\",\"password\":\"passwordxx\"}", username);
        String loginResp = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return om.readTree(loginResp).get("accessToken").asText();
    }

    @Test
    void rejectsUnauthenticated() throws Exception {
        mvc.perform(post("/api/push/device-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"abc\",\"platform\":\"ios\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void persistsTokenForAuthenticatedUser() throws Exception {
        String jwt = registerVerifyLogin("alice@example.com", "alice");

        mvc.perform(post("/api/push/device-token")
                .header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"abc-token\",\"platform\":\"ios\"}"))
            .andExpect(status().isNoContent());

        assertThat(repo.findAll())
            .singleElement()
            .satisfies(dt -> {
                assertThat(dt.getToken()).isEqualTo("abc-token");
                assertThat(dt.getPlatform()).isEqualTo("ios");
                assertThat(dt.getUserId()).isNotNull();
            });

        // idempotent: posting same token again does not duplicate
        mvc.perform(post("/api/push/device-token")
                .header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"abc-token\",\"platform\":\"ios\"}"))
            .andExpect(status().isNoContent());

        assertThat(repo.findAll()).hasSize(1);
    }
}
