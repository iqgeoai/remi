package com.remi.auth.mail;

import java.util.UUID;

public interface MailService {
  void sendVerification(String toEmail, String username, UUID verificationToken);
  void sendPasswordReset(String toEmail, String username, UUID resetToken);
}
