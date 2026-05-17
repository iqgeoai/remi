package com.remi.ws.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final StompAuthChannelInterceptor authInterceptor;
  private final StompSubscriptionInterceptor subInterceptor;
  private final ObjectMapper objectMapper;

  public WebSocketConfig(StompAuthChannelInterceptor authInterceptor,
                          StompSubscriptionInterceptor subInterceptor,
                          ObjectMapper objectMapper) {
    this.authInterceptor = authInterceptor;
    this.subInterceptor = subInterceptor;
    this.objectMapper = objectMapper;
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    config.enableSimpleBroker("/topic", "/queue");
    config.setApplicationDestinationPrefixes("/app");
    config.setUserDestinationPrefix("/user");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(authInterceptor, subInterceptor);
  }

  /**
   * Register a Jackson converter that uses the application's primary ObjectMapper (with
   * polymorphic mixins for Action/ActionResult/DomainEvent from JacksonConfig).
   * Returning false suppresses the framework defaults, which would otherwise prepend
   * a vanilla ObjectMapper without our mixins and fail to deserialize sealed Action variants.
   */
  @Override
  public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
    DefaultContentTypeResolver resolver = new DefaultContentTypeResolver();
    resolver.setDefaultMimeType(MimeTypeUtils.APPLICATION_JSON);
    MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
    converter.setObjectMapper(objectMapper);
    converter.setContentTypeResolver(resolver);
    messageConverters.add(0, converter);
    return false;
  }
}
