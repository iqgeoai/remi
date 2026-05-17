package com.remi.config;

import com.remi.user.api.MockMailServiceTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
@Import(MockMailServiceTestConfig.class)
class CorsConfigTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mvc;

    @Test
    void capacitorIosOriginAllowed() throws Exception {
        mvc.perform(options("/api/auth/login")
                .header(HttpHeaders.ORIGIN, "capacitor://localhost")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "capacitor://localhost"));
    }

    @Test
    void capacitorAndroidOriginAllowed() throws Exception {
        // Set serverPort 8080 so Origin "http://localhost" (port 80) is treated as cross-origin,
        // not same-origin against the mocked request URL.
        mvc.perform(options("/api/auth/login")
                .with(request -> { request.setServerPort(8080); return request; })
                .header(HttpHeaders.ORIGIN, "http://localhost")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost"));
    }

    @Test
    void devLanLiveReloadOriginAllowed() throws Exception {
        mvc.perform(options("/api/auth/login")
                .header(HttpHeaders.ORIGIN, "http://192.168.1.42:8100")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://192.168.1.42:8100"));
    }

    @Test
    void unknownOriginRejected() throws Exception {
        mvc.perform(options("/api/auth/login")
                .header(HttpHeaders.ORIGIN, "http://evil.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
            .andExpect(status().isForbidden());
    }
}
