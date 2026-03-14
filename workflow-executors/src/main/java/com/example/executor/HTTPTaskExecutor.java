package com.example.executor;

import com.example.core.engine.ExecutionContext;
import com.example.core.engine.TaskExecutor;
import com.example.core.enums.TaskType;
import com.example.core.model.TaskExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Executes HTTP/REST API calls as workflow tasks.
 *
 * <p>Supports GET, POST, PUT, DELETE, PATCH methods with:
 *
 * <ul>
 *   <li>Request headers
 *   <li>Request body (JSON/text)
 *   <li>Configurable timeout
 *   <li>Response capture (status, body, headers)
 * </ul>
 *
 * <p><b>Configuration:</b>
 *
 * <pre>
 * {
 *   "url": "https://api.example.com/endpoint",
 *   "method": "POST",                          // Default: GET
 *   "headers": {                                // Optional
 *     "Content-Type": "application/json",
 *     "Authorization": "Bearer token"
 *   },
 *   "body": "{\"key\": \"value\"}",            // Optional, for POST/PUT/PATCH
 *   "timeoutSeconds": 30                       // Optional, default: 30
 * }
 * </pre>
 *
 * <p><b>Context Delta Output:</b>
 *
 * <pre>
 * {
 *   "statusCode": 200,
 *   "responseBody": "...",
 *   "responseHeaders": {...},
 *   "durationMs": 150
 * }
 * </pre>
 */
public class HTTPTaskExecutor implements TaskExecutor {
  private static final Logger logger = LoggerFactory.getLogger(HTTPTaskExecutor.class);

  private final String url;
  private final String method;
  private final Map<String, String> headers;
  private final String body;
  private final int timeoutSeconds;

  private static final HttpClient httpClient =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_2)
          .followRedirects(HttpClient.Redirect.NORMAL)
          .build();

  public HTTPTaskExecutor(Map<String, Object> config) {
    this.url = (String) config.getOrDefault("url", "http://localhost");
    this.method = ((String) config.getOrDefault("method", "GET")).toUpperCase();
    this.timeoutSeconds = (int) config.getOrDefault("timeoutSeconds", 30);

    // Extract headers from config
    Object headersObj = config.get("headers");
    if (headersObj instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, String> headerMap = (Map<String, String>) headersObj;
      this.headers = headerMap;
    } else {
      this.headers = new HashMap<>();
    }

    // Extract body for POST/PUT/PATCH
    this.body = (String) config.get("body");

    // Validate configuration
    validateConfig();
  }

  private void validateConfig() {
    if (url == null || url.trim().isEmpty()) {
      throw new IllegalArgumentException("URL is required for HTTP task");
    }

    if (!url.startsWith("http://") && !url.startsWith("https://")) {
      throw new IllegalArgumentException("URL must start with http:// or https://");
    }

    if (!isValidMethod(method)) {
      throw new IllegalArgumentException(
          "Invalid HTTP method: "
              + method
              + ". Supported: GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS");
    }
  }

  private boolean isValidMethod(String method) {
    return method.equals("GET")
        || method.equals("POST")
        || method.equals("PUT")
        || method.equals("DELETE")
        || method.equals("PATCH")
        || method.equals("HEAD")
        || method.equals("OPTIONS");
  }

  @Override
  public TaskExecutionResult execute(ExecutionContext context) {
    Instant startTime = Instant.now();
    logger.info("Executing HTTP task: {} {}", method, url);

    try {
      // Build HTTP request
      HttpRequest.Builder requestBuilder =
          HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(timeoutSeconds));

      // Add headers
      headers.forEach(requestBuilder::header);

      // Add default Content-Type if body exists and not set
      if (body != null && !headers.containsKey("Content-Type")) {
        requestBuilder.header("Content-Type", "application/json");
      }

      // Set HTTP method and body
      HttpRequest.BodyPublisher bodyPublisher =
          body != null
              ? HttpRequest.BodyPublishers.ofString(body)
              : HttpRequest.BodyPublishers.noBody();

      requestBuilder.method(method, bodyPublisher);

      HttpRequest request = requestBuilder.build();

      // Execute HTTP request
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      Instant endTime = Instant.now();
      long durationMs = Duration.between(startTime, endTime).toMillis();

      // Build context delta with response data
      Map<String, Object> contextDelta = new HashMap<>();
      contextDelta.put("statusCode", response.statusCode());
      contextDelta.put("responseBody", response.body());
      contextDelta.put("durationMs", durationMs);

      // Include response headers
      Map<String, String> responseHeaders = new HashMap<>();
      response
          .headers()
          .map()
          .forEach(
              (key, values) -> {
                responseHeaders.put(key, String.join(", ", values));
              });
      contextDelta.put("responseHeaders", responseHeaders);

      // Check if response is successful (2xx status codes)
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        logger.info(
            "HTTP task completed successfully: {} {} - Status: {}, Duration: {}ms",
            method,
            url,
            response.statusCode(),
            durationMs);

        return TaskExecutionResult.success(
            String.format(
                "HTTP %s %s completed with status %d", method, url, response.statusCode()),
            contextDelta,
            startTime,
            endTime);
      } else {
        // Non-2xx status codes are considered failures
        String errorMessage =
            String.format(
                "HTTP request failed with status %d: %s", response.statusCode(), response.body());

        logger.warn(
            "HTTP task failed: {} {} - Status: {}, Body: {}",
            method,
            url,
            response.statusCode(),
            response.body());

        return TaskExecutionResult.failure(
            errorMessage, new RuntimeException(errorMessage), startTime, endTime);
      }

    } catch (java.net.http.HttpTimeoutException e) {
      Instant endTime = Instant.now();
      logger.error("HTTP task timed out after {}s: {} {}", timeoutSeconds, method, url, e);
      return TaskExecutionResult.failure(
          String.format("HTTP task timed out after %s", timeoutSeconds), e, startTime, endTime);

    } catch (java.net.ConnectException e) {
      logger.error("HTTP task connection failed: {} {}", method, url, e);
      Instant endTime = Instant.now();
      return TaskExecutionResult.failure(
          String.format("HTTP task connection failed %s", url), e, startTime, endTime);

    } catch (Exception e) {
      logger.error("HTTP task execution failed: {} {}", method, url, e);
      Instant endTime = Instant.now();
      return TaskExecutionResult.failure(
          String.format("HTTP task execution failed %s", url), e, startTime, endTime);
    }
  }

  @Override
  public TaskType getType() {
    return TaskType.HTTP;
  }
}
