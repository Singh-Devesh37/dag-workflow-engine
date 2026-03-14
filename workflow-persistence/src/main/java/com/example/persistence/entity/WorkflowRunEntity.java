package com.example.persistence.entity;

import com.example.core.enums.RunStatus;
import com.example.persistence.util.JsonbConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "WORKFLOW_RUNS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowRunEntity {

  @Id
  @Column(name = "RUN_ID", nullable = false)
  private String runId;

  @Column(name = "WORKFLOW_ID", nullable = false)
  private String workflowId;

  @Enumerated(EnumType.STRING)
  @Column(name = "STATUS", nullable = false)
  private RunStatus status;

  @Column(name = "START_TIME")
  private Instant startTime;

  @Column(name = "END_TIME")
  private Instant endTime;

  @Column(name = "DURATION_MILLIS")
  private Long durationMillis;

  @OneToMany(
      mappedBy = "workflowRunEntity",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @Builder.Default
  private List<TaskRunEntity> taskRuns = new ArrayList<>();

  @Column(name = "MERGED_CONTEXT")
  @Convert(converter = JsonbConverter.class)
  private Map<String, Object> mergedContext;

  @Column(name = "METADATA")
  @Convert(converter = JsonbConverter.class)
  private Map<String, Object> metadata;

  @Column(name = "CREATED")
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
