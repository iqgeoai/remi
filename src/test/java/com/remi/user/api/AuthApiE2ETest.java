package com.remi.user.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
@Import(MockMailServiceTestConfig.class)
class AuthApiE2ETest {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper om;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach void resetState() {
    MockMailServiceTestConfig.SENT.clear();
    jdbc.execute("TRUNCATE refresh_tokens, verification_tokens, password_reset_tokens, users CASCADE");
  }

  @Test
  void fullHappyPath_register_verify_login_me_refresh_logout() throws Exception {
    String regBody = """
        {"email":"alice@example.com","username":"alice","password":"passwordxx"}""";
    mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(regBody))
        .andExpect(status().isCreated());

    var verificationToken = MockMailServiceTestConfig.SENT.get(0).token();
    String verifyBody = String.format("{\"token\":\"%s\"}", verificationToken);
    mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content(verifyBody))
        .andExpect(status().isNoContent());

    String loginBody = """
        {"emailOrUsername":"alice","password":"passwordxx"}""";
    String loginResp = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    JsonNode loginJson = om.readTree(loginResp);
    String accessToken = loginJson.get("accessToken").asText();
    String refreshToken = loginJson.get("refreshToken").asText();

    mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("alice"));

    String refreshBody = String.format("{\"refreshToken\":\"%s\"}", refreshToken);
    String refreshResp = mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    String newRefresh = om.readTree(refreshResp).get("refreshToken").asText();

    String logoutBody = String.format("{\"refreshToken\":\"%s\"}", newRefresh);
    mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON).content(logoutBody))
        .andExpect(status().isNoContent());
  }

  @Test
  void registerWithBadEmailReturns400() throws Exception {
    String body = """
        {"email":"not-an-email","username":"user1","password":"passwordxx"}""";
    mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void registerWithShortPasswordReturns400() throws Exception {
    String body = """
        {"email":"a@b.com","username":"user1","password":"short"}""";
    mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void loginUnverifiedUserReturnsSameErrorAsWrongPassword() throws Exception {
    String regBody = """
        {"email":"bob@example.com","username":"bob","password":"passwordxx"}""";
    mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(regBody))
        .andExpect(status().isCreated());

    String loginBody = """
        {"emailOrUsername":"bob","password":"passwordxx"}""";
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void meWithoutTokenReturns401() throws Exception {
    mvc.perform(get("/api/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void meWithMalformedTokenReturns401() throws Exception {
    mvc.perform(get("/api/users/me").header("Authorization", "Bearer not.a.real.token"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void devGamesEndpointStaysOpenWithoutAuth() throws Exception {
    String body = """
        {"numPlayers":2,"mode":"ETALAT","difficulty":"MED","seed":42}""";
    mvc.perform(post("/api/dev/games").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());
  }
}
