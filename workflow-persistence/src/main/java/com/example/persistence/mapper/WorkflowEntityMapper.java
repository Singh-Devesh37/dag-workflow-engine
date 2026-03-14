package com.example.persistence.mapper;

import com.example.core.model.TaskRun;
import com.example.core.model.WorkflowRun;
import com.example.persistence.entity.TaskRunEntity;
import com.example.persistence.entity.WorkflowRunEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WorkflowEntityMapper {

  public WorkflowRunEntity toEntity(WorkflowRun workflowRun) {
    if (workflowRun == null) {
      return null;
    }
    WorkflowRunEntity workflowRunEntity = new WorkflowRunEntity();
      workflowRunEntity.setRunId(workflowRun.getRunId());
      workflowRunEntity.setWorkflowId(workflowRun.getWorkflowId());
      workflowRunEntity.setStatus(workflowRun.getStatus());
      workflowRunEntity.setStartTime(workflowRun.getStartTime());
      workflowRunEntity.setEndTime(workflowRun.getEndTime());
      workflowRunEntity.setDurationMillis(workflowRun.getDurationMillis());
      workflowRunEntity.setMetadata(workflowRun.getMetaData());
      workflowRunEntity.getMergedContext().putAll(workflowRun.getMergedContextSnapshot());
      if(workflowRun.getTaskRuns()!=null && !workflowRun.getTaskRuns().isEmpty()){
          List<TaskRunEntity> taskRunEntities = workflowRun.getTaskRuns().values().stream().map(this::toEntity).toList();
          taskRunEntities.forEach(t -> t.setWorkflowRunEntity(workflowRunEntity));
          workflowRunEntity.setTaskRuns(taskRunEntities);
      }
      return workflowRunEntity;
  }

  public  WorkflowRun fromEntity(WorkflowRunEntity workflowRunEntity) {
    if (workflowRunEntity == null) {
      return null;
    }
    WorkflowRun workflowRun =
        new WorkflowRun(workflowRunEntity.getRunId(), workflowRunEntity.getWorkflowId());
    workflowRun.setStatus(workflowRunEntity.getStatus());
    workflowRun.setStartTime(workflowRunEntity.getStartTime());
    workflowRun.setEndTime(workflowRunEntity.getEndTime());
    workflowRun.setDurationMillis(workflowRunEntity.getDurationMillis());
    workflowRun.getMergedContextSnapshot().putAll(workflowRunEntity.getMergedContext());
    workflowRun.setMetaData(workflowRunEntity.getMetadata());
    workflowRun.setCreated(workflowRunEntity.getCreated());
    workflowRun.setUpdated(workflowRunEntity.getUpdated());

    if(workflowRunEntity.getTaskRuns()!=null && !workflowRunEntity.getTaskRuns().isEmpty()){
        List<TaskRun> taskRuns = workflowRunEntity.getTaskRuns().stream().map(this::fromEntity).toList();
        taskRuns.forEach(workflowRun::putTaskRun);
    }
    return workflowRun;
  }

    public  TaskRunEntity toEntity(TaskRun taskRun) {
        if (taskRun == null) {
            return null;
        }
        TaskRunEntity taskRunEntity = new TaskRunEntity();
        taskRunEntity.setId(taskRun.getId());
        taskRunEntity.setTaskName(taskRun.getTaskName());
        taskRunEntity.setStatus(taskRun.getStatus());
        taskRunEntity.setAttempt(taskRun.getAttempt().get());
        taskRunEntity.setMaxRetries(taskRun.getMaxRetries());
        taskRunEntity.setStartTime(taskRun.getStartTime());
        taskRunEntity.setEndTime(taskRun.getEndTime());
        taskRunEntity.setDurationMillis(taskRun.getDurationMillis());
        taskRunEntity.setErrorMessage(taskRun.getErrorMessage());
        taskRunEntity.setErrorStackTrace(taskRun.getErrorStackTrace());
        taskRunEntity.setMetaData(taskRun.getMetaData());
        taskRunEntity.setContextDelta(taskRun.getContextDelta());
        taskRunEntity.setCreated(taskRun.getCreated());
        taskRunEntity.setUpdated(taskRun.getUpdated());
        return taskRunEntity;
    }

    public  TaskRun fromEntity(TaskRunEntity taskRunEntity) {
        if (taskRunEntity == null) {
            return null;
        }
        TaskRun taskRun = new TaskRun(taskRunEntity.getTaskName(), taskRunEntity.getWorkflowRunEntity().getWorkflowId());
        taskRun.setId(taskRunEntity.getId());
        taskRun.setStatus(taskRunEntity.getStatus());
        taskRun.getAttempt().set(taskRunEntity.getAttempt());
        taskRun.setMaxRetries(taskRunEntity.getMaxRetries());
        taskRun.setStartTime(taskRunEntity.getStartTime());
        taskRun.setEndTime(taskRunEntity.getEndTime());
        taskRun.setDurationMillis(taskRunEntity.getDurationMillis());
        taskRun.setErrorMessage(taskRunEntity.getErrorMessage());
        taskRun.setErrorStackTrace(taskRunEntity.getErrorStackTrace());
        taskRun.setMetaData(taskRunEntity.getMetaData());
        taskRun.setContextDelta(taskRunEntity.getContextDelta());
        taskRun.setCreated(taskRunEntity.getCreated());
        taskRun.setUpdated(taskRunEntity.getUpdated());
        return taskRun;
    }
}
