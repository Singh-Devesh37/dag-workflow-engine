package com.example.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "WORKFLOW_DEFINITION")
@Getter
@Setter
@AllArgsConstructor
@Builder
public class WorkflowDefinitionEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "ID", nullable = false)
  private String id;

  @Column(name = "NAME", nullable = false)
  private String name;

  @Column(name = "VERSION", nullable = false)
  private Integer version;

  @Column(name = "DESCRIPTION", nullable = false)
  private String description;

  @Column(name = "TASK_DEFINITION", columnDefinition = "jsonb")
  private String taskDefinitions;

  @Column(name = "CREATED", nullable = false)
  private Instant created;

  @Column(name = "UPDATED", nullable = false)
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
