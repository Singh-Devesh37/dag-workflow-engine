package com.example.core;

import com.example.core.engine.ExecutionContext;
import com.example.core.engine.WorkflowEngine;
import com.example.core.engine.WorkflowSchedulerService;
import com.example.core.enums.RunStatus;
import com.example.core.exception.WorkflowDefinitionException;
import com.example.core.exception.WorkflowEngineException;
import com.example.core.exception.WorkflowSchedulerException;
import com.example.core.model.TaskNode;
import com.example.core.model.WorkflowDefinition;
import com.example.core.model.WorkflowRun;
import com.example.core.repo.WorkflowDefinitionRepository;
import com.example.core.repo.WorkflowRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Service
public class WorkflowFacade {

  private static final Logger logger = LoggerFactory.getLogger(WorkflowFacade.class);

  private final WorkflowEngine workflowEngine;
  private final WorkflowRunRepository workflowRunRepository;
  private final WorkflowDefinitionRepository workflowDefinitionRepository;
  private final ExecutorService executorService;
  private final WorkflowSchedulerService schedulerService;

  public WorkflowFacade(
      WorkflowEngine workflowEngine,
      WorkflowRunRepository workflowRunRepository,
      WorkflowDefinitionRepository workflowDefinitionRepository,
      ExecutorService executorService,
      WorkflowSchedulerService schedulerService) {
    this.workflowEngine = workflowEngine;
    this.workflowRunRepository = workflowRunRepository;
    this.workflowDefinitionRepository = workflowDefinitionRepository;
    this.executorService = executorService;
    this.schedulerService = schedulerService;
  }

  public String startWorkflow(
      String workflowId, List<TaskNode> tasks, Map<String, Object> initialContext) {
    logger.info("Starting Workflow {} with {} tasks", workflowId, tasks.size());
    final String runId = UUID.randomUUID().toString();
    ExecutionContext ctx = new ExecutionContext();
    if (initialContext != null && !initialContext.isEmpty()) {
      ctx.merge(initialContext);
    }

    WorkflowRun workflowRun = new WorkflowRun(runId, workflowId);
    workflowRun.setStartTime(Instant.now());
    workflowRun.setStatus(RunStatus.RUNNING);
    workflowRunRepository.save(workflowRun);

    executorService.submit(
        () -> {
          try {
            CompletableFuture<WorkflowRun> future =
                workflowEngine.execute(runId, workflowId, tasks, ctx);
            WorkflowRun run = future.join();
            workflowRunRepository.save(run);
            logger.info("Workflow {} executed successfully ", workflowId);
          } catch (Exception e) {
            workflowRun.setStatus(RunStatus.FAILED);
            workflowRun.setEndTime(Instant.now());
            workflowRunRepository.save(workflowRun);
            logger.error("Workflow {} failed at : {} ", workflowId, e.getMessage(), e);
          }
        });

    return runId;
  }

  public String startWorkflowByDefinitionName(
      String workflowDefinitionName, Map<String, Object> initialContext) {
    logger.info("Starting Workflow {}", workflowDefinitionName);
    final String runId = UUID.randomUUID().toString();
    ExecutionContext ctx = new ExecutionContext();
    if (initialContext != null && !initialContext.isEmpty()) {
      ctx.merge(initialContext);
    }

    Optional<WorkflowDefinition> workflowDefinition =
        workflowDefinitionRepository.findByName(workflowDefinitionName);
    if (workflowDefinition.isEmpty()) {
      throw new WorkflowDefinitionException(
          "Now Workflow Definition found for Name : " + workflowDefinitionName);
    }
    WorkflowRun workflowRun = new WorkflowRun(runId, workflowDefinition.get().getId());
    workflowRun.setStartTime(Instant.now());
    workflowRun.setStatus(RunStatus.RUNNING);
    workflowRunRepository.save(workflowRun);

    executorService.submit(
        () -> {
          try {
            CompletableFuture<WorkflowRun> future =
                workflowEngine.execute(
                    runId,
                    workflowDefinition.get().getId(),
                    workflowDefinition.get().getTasks().values().stream().toList(),
                    ctx);
            WorkflowRun run = future.join();
            workflowRunRepository.save(run);
            logger.info("Workflow {} executed successfully ", workflowDefinitionName);
          } catch (Exception e) {
            workflowRun.setStatus(RunStatus.FAILED);
            workflowRun.setEndTime(Instant.now());
            workflowRunRepository.save(workflowRun);
            logger.error("Workflow {} failed at : {} ", workflowDefinitionName, e.getMessage(), e);
          }
        });

    return runId;
  }

  public Optional<WorkflowRun> getWorkflowRun(String runId) {
    return workflowRunRepository.findById(runId);
  }

  public List<WorkflowRun> getAllRuns() {
    return workflowRunRepository.findAll();
  }

  public WorkflowDefinition saveWorkflowDefinition(WorkflowDefinition definition) {
    WorkflowDefinition savedDefinition = workflowDefinitionRepository.save(definition);
    logger.info("Workflow {} saved successfully", savedDefinition.getName());
    return savedDefinition;
  }

    public Optional<WorkflowDefinition> getWorkflowDefinition(String name) {
        return workflowDefinitionRepository.findByName(name);
    }

    public List<WorkflowDefinition> getAllWorkflowDefinitions() {
        return workflowDefinitionRepository.findAll();
    }


    public void scheduleWorkflow(
      String workflowId, Map<String, Object> ctx, String cronExp) {
    try{
        schedulerService.scheduleWorkflow(workflowId,cronExp, ctx);
        logger.info("Workflow {} scheduled at {} successfully", workflowId, cronExp);
    }catch (WorkflowSchedulerException e){
        throw new WorkflowEngineException("Unable to schedule Workflow :" +workflowId);
    }

  }
}
