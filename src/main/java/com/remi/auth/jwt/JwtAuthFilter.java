package com.remi.auth.jwt;

import com.remi.auth.domain.JwtClaims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
  public static final String EXPIRED_HEADER = "X-Token-Expired";
  private final JwtService jwt;

  public JwtAuthFilter(JwtService jwt) { this.jwt = jwt; }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
      throws ServletException, IOException {
    String header = req.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring("Bearer ".length());
      try {
        JwtClaims claims = jwt.parseAccessToken(token);
        var auth = new UsernamePasswordAuthenticationToken(claims.userId(), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
      } catch (ExpiredJwtException e) {
        resp.setHeader(EXPIRED_HEADER, "true");
        SecurityContextHolder.clearContext();
      } catch (JwtException e) {
        SecurityContextHolder.clearContext();
      }
    }
    chain.doFilter(req, resp);
  }
}
