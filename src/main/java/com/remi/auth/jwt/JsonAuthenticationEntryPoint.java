package com.remi.auth.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Map;

@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {
  private final ObjectMapper om;
  public JsonAuthenticationEntryPoint(ObjectMapper om) { this.om = om; }

  @Override
  public void commence(HttpServletRequest req, HttpServletResponse resp, AuthenticationException e)
      throws IOException {
    String code = "true".equals(resp.getHeader(JwtAuthFilter.EXPIRED_HEADER)) ? "TOKEN_EXPIRED" : "UNAUTHORIZED";
    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
    om.writeValue(resp.getOutputStream(), Map.of("code", code, "message", "Autentificare necesară."));
  }
}
