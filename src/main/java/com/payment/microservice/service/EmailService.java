package com.payment.microservice.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

  private final JavaMailSender mailSender;

  /** Plain-text email. */
  public boolean send(String to, String subject, String body) {
    if (to == null || to.isBlank()) {
      log.warn("Email skipped: no recipient configured");
      return false;
    }
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setTo(to);
      message.setSubject(subject);
      message.setText(body);
      mailSender.send(message);
      log.info("Email sent to {}: {}", to, subject);
      return true;
    } catch (Exception e) {
      log.error("Email send failed to {}: {}", to, e.getMessage());
      return false;
    }
  }

  /** HTML email (used by receipt templates). */
  public boolean sendHtml(String to, String subject, String html) {
    if (to == null || to.isBlank()) {
      log.warn("HTML email skipped: no recipient");
      return false;
    }
    try {
      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(html, true);
      mailSender.send(mimeMessage);
      log.info("HTML email sent to {}: {}", to, subject);
      return true;
    } catch (Exception e) {
      log.error("HTML email send failed to {}: {}", to, e.getMessage());
      return false;
    }
  }

  /** Loads a template from classpath:static/email/{name}.html */
  public String loadTemplate(String name) {
    try {
      ClassPathResource resource = new ClassPathResource("static/email/" + name + ".html");
      return new String(
          resource.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.error("Failed to load email template {}: {}", name, e.getMessage());
      return "";
    }
  }

  /** Replaces {{key}} placeholders. */
  public static String fill(String template, java.util.Map<String, String> vars) {
    if (template == null) return "";
    String out = template;
    if (vars != null) {
      for (java.util.Map.Entry<String, String> e : vars.entrySet()) {
        String val = e.getValue() == null ? "" : e.getValue();
        out = out.replace("{{" + e.getKey() + "}}", val);
      }
    }
    return out;
  }
}
