package com.remi.ws.config;

import com.remi.auth.domain.JwtClaims;
import com.remi.auth.jwt.JwtService;
import io.jsonwebtoken.JwtException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {
  private final JwtService jwt;

  public StompAuthChannelInterceptor(JwtService jwt) { this.jwt = jwt; }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
      String authHeader = accessor.getFirstNativeHeader("Authorization");
      if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        throw new MessagingException("Missing or malformed Authorization header");
      }
      String token = authHeader.substring("Bearer ".length());
      try {
        JwtClaims claims = jwt.parseAccessToken(token);
        accessor.setUser(new StompPrincipal(claims.userId()));
      } catch (JwtException e) {
        throw new MessagingException("Invalid JWT: " + e.getMessage());
      }
    }
    return message;
  }
}
