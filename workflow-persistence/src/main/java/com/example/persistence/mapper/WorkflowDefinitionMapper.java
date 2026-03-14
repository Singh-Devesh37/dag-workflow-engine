package com.example.persistence.mapper;

import com.example.core.exception.WorkflowDefinitionException;
import com.example.core.model.TaskNode;
import com.example.core.model.WorkflowDefinition;
import com.example.persistence.entity.WorkflowDefinitionEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class WorkflowDefinitionMapper {
  private static final Logger logger = LoggerFactory.getLogger(WorkflowDefinitionMapper.class);
  private final ObjectMapper mapper = new ObjectMapper();

  public WorkflowDefinitionEntity toEntity(WorkflowDefinition definition)
      throws WorkflowDefinitionException {
    String taskDefinition = null;
    try {
      taskDefinition = mapper.writeValueAsString(definition.getTasks());
    } catch (JsonProcessingException e) {
      logger.error("Unable to save Workflow Definitions for Workflow : {}", definition.getName());
      throw new WorkflowDefinitionException("Unable to parse Task Definition for Workflow", e);
    }
    return WorkflowDefinitionEntity.builder()
        .id(definition.getId())
        .name(definition.getName())
        .version(definition.getVersion())
        .description(definition.getDescription())
        .taskDefinitions(taskDefinition)
        .created(definition.getCreated())
        .updated(definition.getUpdated())
        .build();
  }

  public WorkflowDefinition fromEntity(WorkflowDefinitionEntity entity)
      throws WorkflowDefinitionException {
    Map<String, TaskNode> taskDefinition = null;
    try {
      taskDefinition = mapper.readValue(entity.getTaskDefinitions(), new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      logger.error("Unable to create Workflow Definitions for Workflow : {}", entity.getName());
      throw new WorkflowDefinitionException("Unable to parse Task Definition for Workflow", e);
    }
    return new WorkflowDefinition(
        entity.getId(),
        entity.getName(),
        entity.getVersion(),
        entity.getDescription(),
        taskDefinition,
        entity.getCreated(),
        entity.getUpdated());
  }
}
