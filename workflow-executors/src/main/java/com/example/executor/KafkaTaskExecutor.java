package com.example.executor;

import com.example.core.engine.ExecutionContext;
import com.example.core.engine.TaskExecutor;
import com.example.core.enums.TaskType;
import com.example.core.model.TaskExecutionResult;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Publishes messages to Apache Kafka topics as workflow tasks.
 *
 * <p>Supports:
 *
 * <ul>
 *   <li>Message publishing to Kafka topics
 *   <li>Optional message key for partitioning
 *   <li>Custom headers
 *   <li>Configurable timeout
 *   <li>Sync/async publishing
 * </ul>
 *
 * <p><b>Configuration:</b>
 *
 * <pre>
 * {
 *   "bootstrapServers": "localhost:9092",         // Required
 *   "topic": "workflow-events",                   // Required
 *   "message": "Event payload",                   // Required
 *   "key": "message-key",                         // Optional, for partitioning
 *   "headers": {                                   // Optional
 *     "source": "workflow-engine",
 *     "event-type": "task-completed"
 *   },
 *   "timeoutSeconds": 30,                         // Optional, default: 30
 *   "async": false                                // Optional, default: false (sync)
 * }
 * </pre>
 *
 * <p><b>Context Delta Output:</b>
 *
 * <pre>
 * {
 *   "partition": 0,
 *   "offset": 12345,
 *   "timestamp": 1234567890,
 *   "topic": "workflow-events",
 *   "durationMs": 150
 * }
 * </pre>
 */
public class KafkaTaskExecutor implements TaskExecutor {
  private static final Logger logger = LoggerFactory.getLogger(KafkaTaskExecutor.class);

  private final String bootstrapServers;
  private final String topic;
  private final String message;
  private final String key;
  private final Map<String, String> headers;
  private final int timeoutSeconds;
  private final boolean async;

  public KafkaTaskExecutor(Map<String, Object> config) {
    this.bootstrapServers = (String) config.get("bootstrapServers");
    this.topic = (String) config.get("topic");
    this.message = (String) config.get("message");
    this.key = (String) config.get("key"); // Optional
    this.timeoutSeconds = (int) config.getOrDefault("timeoutSeconds", 30);
    this.async = (boolean) config.getOrDefault("async", false);

    // Extract headers
    Object headersObj = config.get("headers");
    if (headersObj instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, String> headerMap = (Map<String, String>) headersObj;
      this.headers = new HashMap<>(headerMap);
    } else {
      this.headers = new HashMap<>();
    }

    validateConfig();
  }

  private void validateConfig() {
    if (bootstrapServers == null || bootstrapServers.trim().isEmpty()) {
      throw new IllegalArgumentException("bootstrapServers is required for Kafka task");
    }

    if (topic == null || topic.trim().isEmpty()) {
      throw new IllegalArgumentException("topic is required for Kafka task");
    }

    if (message == null) {
      throw new IllegalArgumentException("message is required for Kafka task");
    }

    if (timeoutSeconds <= 0) {
      throw new IllegalArgumentException("timeoutSeconds must be positive");
    }
  }

  @Override
  public TaskExecutionResult execute(ExecutionContext context) {
    Instant startTime = Instant.now();
    logger.info("Publishing Kafka message to topic: {}", topic);

    KafkaProducer<String, String> producer = null;

    try {
      // Create Kafka producer
      Properties props = new Properties();
      props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
      props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
      props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
      props.put(ProducerConfig.ACKS_CONFIG, "all"); // Wait for all replicas
      props.put(ProducerConfig.RETRIES_CONFIG, 3); // Retry on failures
      props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, timeoutSeconds * 1000);
      props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, (timeoutSeconds + 10) * 1000);

      producer = new KafkaProducer<>(props);

      // Create producer record
      ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, message);

      // Add custom headers
      headers.forEach(
          (headerKey, headerValue) -> {
            record.headers().add(headerKey, headerValue.getBytes());
          });

      // Send message
      Future<RecordMetadata> future = producer.send(record);

      RecordMetadata metadata;
      if (async) {
        // Async mode: just trigger send, don't wait
        logger.info("Kafka message sent asynchronously to topic: {}", topic);
        metadata = null; // Will complete in background
      } else {
        // Sync mode: wait for acknowledgment
        metadata = future.get(timeoutSeconds, TimeUnit.SECONDS);
      }

      Instant endTime = Instant.now();
      long durationMs = Duration.between(startTime, endTime).toMillis();

      // Build context delta
      Map<String, Object> contextDelta = new HashMap<>();
      contextDelta.put("topic", topic);
      contextDelta.put("durationMs", durationMs);
      contextDelta.put("async", async);

      if (metadata != null) {
        contextDelta.put("partition", metadata.partition());
        contextDelta.put("offset", metadata.offset());
        contextDelta.put("timestamp", metadata.timestamp());

        logger.info(
            "Kafka message published successfully - Topic: {}, Partition: {}, Offset: {}, Duration: {}ms",
            topic,
            metadata.partition(),
            metadata.offset(),
            durationMs);
      }

      return TaskExecutionResult.success(
          String.format("Kafka message published to topic %s", topic),
          contextDelta,
          startTime,
          endTime);

    } catch (java.util.concurrent.TimeoutException e) {
      logger.error("Kafka publish timed out after {}s - Topic: {}", timeoutSeconds, topic, e);
      Instant endTime = Instant.now();
      return TaskExecutionResult.failure(
          String.format("Kafka publish timed out after %d seconds", timeoutSeconds),
          e,
          startTime,
          endTime);

    } catch (org.apache.kafka.common.errors.TimeoutException e) {
      logger.error("Kafka timeout - Topic: {}", topic, e);
      Instant endTime = Instant.now();
      return TaskExecutionResult.failure(
          "Kafka broker timeout: " + e.getMessage(), e, startTime, endTime);

    } catch (org.apache.kafka.common.KafkaException e) {
      logger.error("Kafka error - Topic: {}", topic, e);
      Instant endTime = Instant.now();
      return TaskExecutionResult.failure("Kafka error: " + e.getMessage(), e, startTime, endTime);

    } catch (Exception e) {
      logger.error("Failed to publish Kafka message - Topic: {}", topic, e);
      Instant endTime = Instant.now();
      return TaskExecutionResult.failure(
          String.format("Kafka publish failed: %s", e.getMessage()), e, startTime, endTime);

    } finally {
      // Close producer
      if (producer != null) {
        try {
          producer.close(Duration.ofSeconds(5));
        } catch (Exception e) {
          logger.warn("Error closing Kafka producer", e);
        }
      }
    }
  }

  @Override
  public TaskType getType() {
    return TaskType.KAFKA;
  }
}
