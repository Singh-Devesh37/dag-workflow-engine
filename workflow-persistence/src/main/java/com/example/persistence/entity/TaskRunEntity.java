package com.example.persistence.entity;

import com.example.core.enums.RunStatus;
import com.example.persistence.util.JsonbConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "TASK_RUNS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskRunEntity {

  @Id
  @Column(name = "ID", nullable = false)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "WORKFLOW_ID", nullable = false)
  private WorkflowRunEntity workflowRunEntity;

  @Column(name = "TASK_NAME", nullable = false)
  private String taskName;

  @Enumerated(EnumType.STRING)
  @Column(name = "STATUS", nullable = false)
  private RunStatus status;

  @Column(name = "ATTEMPT")
  private Integer attempt;

  @Column(name = "MAX_RETRIES")
  private Integer maxRetries;

  @Column(name = "START_TIME")
  private Instant startTime;

  @Column(name = "END_TIME")
  private Instant endTime;

  @Column(name = "DURATION_MILLIS")
  private Long durationMillis;

  @Column(name = "ERROR", columnDefinition = "text")
  private String errorMessage;

  @Column(name = "ERROR_DETAILS", columnDefinition = "text")
  private String errorStackTrace;

  @Column(name = "MERGED_CONTEXT", columnDefinition = "jsonb")
  @Convert(converter = JsonbConverter.class)
  private Map<String, Object> contextDelta;

  @Column(name = "METADATA", columnDefinition = "jsonb")
  @Convert(converter = JsonbConverter.class)
  private Map<String, Object> metaData;

  @Column(name = "CREATED", updatable = false)
  private Instant created;

  @Column(name = "UPDATED")
  private Instant updated;

  @PrePersist
  public void prePersist() {
    Instant now = Instant.now();
    this.created = now;
    this.updated = now;
  }

  @PreUpdate
  public void preUpdate() {
    this.updated = Instant.now();
  }
}
