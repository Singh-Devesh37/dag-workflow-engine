package com.example.executor;

import com.example.core.engine.ExecutionContext;
import com.example.core.engine.TaskExecutor;
import com.example.core.enums.TaskType;
import com.example.core.model.TaskExecutionResult;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Sends emails via SMTP as workflow tasks.
 *
 * <p>Supports:
 *
 * <ul>
 *   <li>Simple text emails
 *   <li>HTML emails
 *   <li>Multiple recipients (To, CC, BCC)
 *   <li>Custom headers
 *   <li>SMTP authentication
 * </ul>
 *
 * <p><b>Configuration:</b>
 *
 * <pre>
 * {
 *   "smtp": {
 *     "host": "smtp.gmail.com",
 *     "port": 587,
 *     "username": "user@gmail.com",
 *     "password": "password",
 *     "starttls": true
 *   },
 *   "from": "sender@example.com",                // Required
 *   "to": "recipient@example.com",               // Required, or array of emails
 *   "cc": "cc@example.com",                      // Optional
 *   "bcc": "bcc@example.com",                    // Optional
 *   "subject": "Workflow Notification",          // Required
 *   "body": "Message body",                      // Required
 *   "isHtml": false                              // Optional, default: false
 * }
 * </pre>
 *
 * <p><b>Context Delta Output:</b>
 *
 * <pre>
 * {
 *   "emailSent": true,
 *   "recipient": "recipient@example.com",
 *   "subject": "Workflow Notification",
 *   "durationMs": 1500
 * }
 * </pre>
 *
 * <p><b>Note:</b> For production use, store SMTP credentials securely (environment variables,
 * secrets manager).
 */
public class EmailTaskExecutor implements TaskExecutor {
  private static final Logger logger = LoggerFactory.getLogger(EmailTaskExecutor.class);

  private final Map<String, Object> smtpConfig;
  private final String from;
  private final List<String> to;
  private final List<String> cc;
  private final List<String> bcc;
  private final String subject;
  private final String body;
  private final boolean isHtml;

  public EmailTaskExecutor(Map<String, Object> config) {
    // Extract SMTP configuration
    Object smtpObj = config.get("smtp");
    if (smtpObj instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> smtpMap = (Map<String, Object>) smtpObj;
      this.smtpConfig = new HashMap<>(smtpMap);
    } else {
      this.smtpConfig = new HashMap<>();
    }

    this.from = (String) config.get("from");
    this.subject = (String) config.get("subject");
    this.body = (String) config.get("body");
    this.isHtml = (boolean) config.getOrDefault("isHtml", false);

    // Extract recipients (can be String or List)
    this.to = extractEmailList(config.get("to"));
    this.cc = extractEmailList(config.get("cc"));
    this.bcc = extractEmailList(config.get("bcc"));

    validateConfig();
  }

  private List<String> extractEmailList(Object emailObj) {
    if (emailObj == null) {
      return new ArrayList<>();
    } else if (emailObj instanceof String) {
      return List.of((String) emailObj);
    } else if (emailObj instanceof List) {
      @SuppressWarnings("unchecked")
      List<String> emailList = (List<String>) emailObj;
      return new ArrayList<>(emailList);
    } else {
      return new ArrayList<>();
    }
  }

  private void validateConfig() {
    if (from == null || from.trim().isEmpty()) {
      throw new IllegalArgumentException("'from' email address is required");
    }

    if (to == null || to.isEmpty()) {
      throw new IllegalArgumentException("At least one 'to' recipient is required");
    }

    if (subject == null) {
      throw new IllegalArgumentException("Email 'subject' is required");
    }

    if (body == null) {
      throw new IllegalArgumentException("Email 'body' is required");
    }

    // Validate SMTP config if provided
    if (!smtpConfig.isEmpty()) {
      if (!smtpConfig.containsKey("host")) {
        throw new IllegalArgumentException("SMTP 'host' is required in smtp config");
      }
    }
  }

  @Override
  public TaskExecutionResult execute(ExecutionContext context) {
    Instant startTime = Instant.now();
    logger.info("Sending email - Subject: '{}', To: {}", subject, to);

    try {
      // Create mail session
      Session session = createMailSession();

      // Create message
      Message message = new MimeMessage(session);
      message.setFrom(new InternetAddress(from));

      // Add To recipients
      for (String recipient : to) {
        message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
      }

      // Add CC recipients
      for (String recipient : cc) {
        message.addRecipient(Message.RecipientType.CC, new InternetAddress(recipient));
      }

      // Add BCC recipients
      for (String recipient : bcc) {
        message.addRecipient(Message.RecipientType.BCC, new InternetAddress(recipient));
      }

      // Set subject and body
      message.setSubject(subject);

      if (isHtml) {
        message.setContent(body, "text/html; charset=utf-8");
      } else {
        message.setText(body);
      }

      // Set sent date
      message.setSentDate(new Date());

      // Send message
      Transport.send(message);

      Instant endTime = Instant.now();
      long durationMs = Duration.between(startTime, endTime).toMillis();

      logger.info(
          "Email sent successfully - Subject: '{}', To: {}, Duration: {}ms",
          subject,
          to,
          durationMs);

      // Build context delta
      Map<String, Object> contextDelta = new HashMap<>();
      contextDelta.put("emailSent", true);
      contextDelta.put("recipients", to);
      contextDelta.put("subject", subject);
      contextDelta.put("durationMs", durationMs);

      return TaskExecutionResult.success(
          String.format("Email sent successfully to %s", String.join(", ", to)),
          contextDelta,
          startTime,
          endTime);

    } catch (MessagingException e) {
      logger.error("Failed to send email - Subject: '{}'", subject, e);

      String errorMessage;
      if (e.getCause() instanceof java.net.UnknownHostException) {
        errorMessage = "SMTP server not found: " + e.getMessage();
      } else if (e.getCause() instanceof java.net.ConnectException) {
        errorMessage = "Failed to connect to SMTP server: " + e.getMessage();
      } else if (e instanceof AuthenticationFailedException) {
        errorMessage = "SMTP authentication failed. Check username/password.";
      } else {
        errorMessage = "Failed to send email: " + e.getMessage();
      }
      Instant endTime = Instant.now();
      return TaskExecutionResult.failure(errorMessage, e, startTime, endTime);

    } catch (Exception e) {
      logger.error("Unexpected error sending email - Subject: '{}'", subject, e);
      Instant endTime = Instant.now();
      return TaskExecutionResult.failure(
          String.format("Email send failed: %s", e.getMessage()), e, startTime, endTime);
    }
  }

  private Session createMailSession() {
    Properties props = new Properties();

    if (!smtpConfig.isEmpty()) {
      // Use provided SMTP configuration
      String host = (String) smtpConfig.get("host");
      Integer port = (Integer) smtpConfig.getOrDefault("port", 587);
      String username = (String) smtpConfig.get("username");
      String password = (String) smtpConfig.get("password");
      Boolean starttls = (Boolean) smtpConfig.getOrDefault("starttls", true);
      Boolean auth = (Boolean) smtpConfig.getOrDefault("auth", username != null);

      props.put("mail.smtp.host", host);
      props.put("mail.smtp.port", port.toString());
      props.put("mail.smtp.auth", auth.toString());

      if (starttls) {
        props.put("mail.smtp.starttls.enable", "true");
      }

      // SSL configuration (optional)
      Boolean ssl = (Boolean) smtpConfig.getOrDefault("ssl", false);
      if (ssl) {
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
      }

      // Create session with authentication
      if (auth && username != null && password != null) {
        return Session.getInstance(
            props,
            new Authenticator() {
              @Override
              protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
              }
            });
      } else {
        return Session.getInstance(props);
      }
    } else {
      // Use default SMTP configuration (localhost:25)
      props.put("mail.smtp.host", "localhost");
      props.put("mail.smtp.port", "25");
      return Session.getInstance(props);
    }
  }

  @Override
  public TaskType getType() {
    return TaskType.EMAIL;
  }
}
