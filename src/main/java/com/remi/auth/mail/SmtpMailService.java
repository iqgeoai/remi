package com.remi.auth.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@Profile("!test")
public class SmtpMailService implements MailService {
  private final JavaMailSender sender;
  private final String from;
  private final String verifyBase;
  private final String resetBase;

  public SmtpMailService(JavaMailSender sender,
                         @Value("${mail.from}") String from,
                         @Value("${mail.verification-link-base}") String verifyBase,
                         @Value("${mail.reset-link-base}") String resetBase) {
    this.sender = sender;
    this.from = from;
    this.verifyBase = verifyBase;
    this.resetBase = resetBase;
  }

  @Override
  public void sendVerification(String toEmail, String username, UUID token) {
    SimpleMailMessage msg = new SimpleMailMessage();
    msg.setFrom(from);
    msg.setTo(toEmail);
    msg.setSubject("Verifică adresa de email — Remi");
    msg.setText("Salut " + username + ",\n\nClick pentru a-ți verifica emailul: "
        + verifyBase + "?token=" + token + "\n\nLinkul expiră în 24 ore.");
    sender.send(msg);
  }

  @Override
  public void sendPasswordReset(String toEmail, String username, UUID token) {
    SimpleMailMessage msg = new SimpleMailMessage();
    msg.setFrom(from);
    msg.setTo(toEmail);
    msg.setSubject("Resetare parolă — Remi");
    msg.setText("Salut " + username + ",\n\nClick pentru a-ți reseta parola: "
        + resetBase + "?token=" + token + "\n\nLinkul expiră în 1 oră. Dacă nu ai cerut tu, ignoră acest email.");
    sender.send(msg);
  }
}
