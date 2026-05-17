package com.remi.user.api;

import com.remi.auth.mail.MailService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@TestConfiguration
public class MockMailServiceTestConfig {
  public record SentMail(String kind, String toEmail, String username, UUID token) {}
  public static final List<SentMail> SENT = new CopyOnWriteArrayList<>();

  @Bean @Primary
  public MailService mockMailService() {
    return new MailService() {
      @Override public void sendVerification(String toEmail, String username, UUID token) {
        SENT.add(new SentMail("VERIFICATION", toEmail, username, token));
      }
      @Override public void sendPasswordReset(String toEmail, String username, UUID token) {
        SENT.add(new SentMail("PASSWORD_RESET", toEmail, username, token));
      }
    };
  }
}
