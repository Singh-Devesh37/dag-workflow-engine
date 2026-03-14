package com.example.executor;

import com.example.core.engine.ExecutionContext;
import com.example.core.engine.TaskExecutor;
import com.example.core.enums.TaskType;
import com.example.core.exception.TaskTimeoutException;
import com.example.core.model.TaskExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Executes shell scripts and commands as workflow tasks.
 *
 * <p>Supports execution of:
 *
 * <ul>
 *   <li>Bash scripts
 *   <li>Python scripts
 *   <li>Node.js scripts
 *   <li>Any executable command
 * </ul>
 *
 * <p><b>Configuration:</b>
 *
 * <pre>
 * {
 *   "command": "python3 script.py arg1 arg2",  // Command to execute
 *   "workingDir": "/path/to/dir",              // Optional, default: current dir
 *   "timeoutSeconds": 60,                      // Optional, default: 60
 *   "env": {                                    // Optional environment variables
 *     "VAR_NAME": "value"
 *   }
 * }
 * </pre>
 *
 * <p><b>Alternative Configuration (script array):</b>
 *
 * <pre>
 * {
 *   "script": ["python3", "script.py", "arg1", "arg2"],
 *   "timeoutSeconds": 60
 * }
 * </pre>
 *
 * <p><b>Context Delta Output:</b>
 *
 * <pre>
 * {
 *   "exitCode": 0,
 *   "stdout": "script output",
 *   "stderr": "error output",
 *   "durationMs": 1500,
 *   "timedOut": false
 * }
 * </pre>
 *
 * <p><b>Exit Codes:</b>
 *
 * <ul>
 *   <li>0 = Success
 *   <li>Non-zero = Failure
 * </ul>
 */
public class ScriptTaskExecutor implements TaskExecutor {
  private static final Logger logger = LoggerFactory.getLogger(ScriptTaskExecutor.class);

  private final List<String> command;
  private final String workingDir;
  private final int timeoutSeconds;
  private final Map<String, String> environment;

  public ScriptTaskExecutor(Map<String, Object> config) {
    // Extract command (either as string or array)
    Object commandObj = config.get("command");
    Object scriptObj = config.get("script");

    if (commandObj instanceof String commandStr) {
      // Parse command string into parts
      this.command = parseCommand(commandStr);
    } else if (scriptObj instanceof List) {
      // Use script array directly
      @SuppressWarnings("unchecked")
      List<String> scriptList = (List<String>) scriptObj;
      this.command = new ArrayList<>(scriptList);
    } else if (commandObj instanceof List) {
      @SuppressWarnings("unchecked")
      List<String> commandList = (List<String>) commandObj;
      this.command = new ArrayList<>(commandList);
    } else {
      throw new IllegalArgumentException(
          "Either 'command' (String) or 'script' (Array) must be provided");
    }

    this.workingDir = (String) config.get("workingDir");
    this.timeoutSeconds = (int) config.getOrDefault("timeoutSeconds", 60);

    // Extract environment variables
    Object envObj = config.get("env");
    if (envObj instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, String> envMap = (Map<String, String>) envObj;
      this.environment = new HashMap<>(envMap);
    } else {
      this.environment = new HashMap<>();
    }

    validateConfig();
  }

  private void validateConfig() {
    if (command == null || command.isEmpty()) {
      throw new IllegalArgumentException("Command cannot be empty");
    }

    if (timeoutSeconds <= 0) {
      throw new IllegalArgumentException("Timeout must be positive");
    }

    if (workingDir != null) {
      File dir = new File(workingDir);
      if (!dir.exists() || !dir.isDirectory()) {
        throw new IllegalArgumentException(
            "Working directory does not exist or is not a directory: " + workingDir);
      }
    }
  }

  /**
   * Parse command string into parts, respecting quoted arguments. Example: "python3 script.py \"arg
   * with spaces\"" → ["python3", "script.py", "arg with spaces"]
   */
  private List<String> parseCommand(String commandStr) {
    List<String> parts = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;

    for (char c : commandStr.toCharArray()) {
      if (c == '"' || c == '\'') {
        inQuotes = !inQuotes;
      } else if (c == ' ' && !inQuotes) {
        if (!current.isEmpty()) {
          parts.add(current.toString());
          current = new StringBuilder();
        }
      } else {
        current.append(c);
      }
    }

    if (!current.isEmpty()) {
      parts.add(current.toString());
    }

    return parts;
  }

  @Override
  public TaskExecutionResult execute(ExecutionContext context) {
    Instant startTime = Instant.now();
    String commandStr = String.join(" ", command);
    logger.info("Executing script: {}", commandStr);

    Process process = null;
    boolean timedOut = false;

    try {
      // Build process
      ProcessBuilder pb = new ProcessBuilder(command);

      // Set working directory if specified
      if (workingDir != null) {
        pb.directory(new File(workingDir));
      }

      // Add environment variables
      if (!environment.isEmpty()) {
        Map<String, String> env = pb.environment();
        env.putAll(environment);
      }

      // Redirect error stream to capture both stdout and stderr separately
      pb.redirectErrorStream(false);

      // Start process
      process = pb.start();

      // Capture stdout
      BufferedReader stdoutReader =
          new BufferedReader(new InputStreamReader(process.getInputStream()));
      String stdout = stdoutReader.lines().collect(Collectors.joining("\n"));

      // Capture stderr
      BufferedReader stderrReader =
          new BufferedReader(new InputStreamReader(process.getErrorStream()));
      String stderr = stderrReader.lines().collect(Collectors.joining("\n"));

      // Wait for process with timeout
      boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

      Instant endTime = Instant.now();
      long durationMs = Duration.between(startTime, endTime).toMillis();

      if (!finished) {
        // Process timed out
        process.destroyForcibly();
        logger.error("Script timed out after {}s: {}", timeoutSeconds, commandStr);

        return TaskExecutionResult.failure(
            String.format("Script execution timed out after %d seconds", timeoutSeconds),
            new TaskTimeoutException(
                String.format("Script execution timed out after %d seconds", timeoutSeconds)),
            startTime,
            endTime);
      }

      // Get exit code
      int exitCode = process.exitValue();

      // Build context delta
      Map<String, Object> contextDelta = new HashMap<>();
      contextDelta.put("exitCode", exitCode);
      contextDelta.put("stdout", stdout);
      contextDelta.put("stderr", stderr);
      contextDelta.put("durationMs", durationMs);
      contextDelta.put("timedOut", false);

      // Check exit code
      if (exitCode == 0) {
        logger.info("Script completed successfully: {} - Duration: {}ms", commandStr, durationMs);

        return TaskExecutionResult.success(
            String.format("Script executed successfully: %s", commandStr),
            contextDelta,
            startTime,
            endTime);
      } else {
        logger.warn(
            "Script failed with exit code {}: {} - stderr: {}", exitCode, commandStr, stderr);

        return TaskExecutionResult.failure(
            String.format("Script failed with exit code %d: %s", exitCode, stderr),
            new RuntimeException("Script execution failed with exit code " + exitCode),
            startTime,
            endTime);
      }

    } catch (TaskTimeoutException e) {
      // Already logged, just rethrow wrapped in TaskExecutionResult
      Instant endTime = Instant.now();
      return TaskExecutionResult.failure(e.getMessage(), e, startTime, endTime);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.error("Script execution interrupted: {}", commandStr, e);
      Instant endTime = Instant.now();
      return TaskExecutionResult.failure(
          String.format("Script execution interrupted: %s", e.getMessage()), e, startTime, endTime);

    } catch (Exception e) {
      logger.error("Script execution failed: {}", commandStr, e);
      Instant endTime = Instant.now();
      return TaskExecutionResult.failure(
          String.format("Script execution failed: %s", e.getMessage()), e, startTime, endTime);

    } finally {
      // Ensure process is destroyed
      if (process != null && process.isAlive()) {
        process.destroyForcibly();
      }
    }
  }

  @Override
  public TaskType getType() {
    return TaskType.SCRIPT;
  }
}
