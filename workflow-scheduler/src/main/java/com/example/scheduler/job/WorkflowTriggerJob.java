package com.example.scheduler.job;

import com.example.core.WorkflowFacade;
import com.example.core.exception.WorkflowEngineException;
import com.example.core.pub.WorkflowEventPublisher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WorkflowTriggerJob implements Job {

  private static final Logger logger = LoggerFactory.getLogger(WorkflowTriggerJob.class);

  @Autowired private WorkflowFacade workflowFacade;

  @Override
  public void execute(JobExecutionContext context) throws WorkflowEngineException {
    try {
      JobDataMap dataMap = context.getMergedJobDataMap();

      String workflowId = dataMap.getString("workflowId");
      String initialContextJson = dataMap.getString("ctx");

      Map<String, Object> contextMap =
          new ObjectMapper()
              .readValue(initialContextJson, new TypeReference<Map<String, Object>>() {});
      String runId = workflowFacade.startWorkflowByDefinitionName(workflowId, contextMap);
      logger.info("Workflow {} started with runId {}", workflowId, runId);
    } catch (Exception e) {
      throw new WorkflowEngineException("Error in Executing Scheduled Workflow", e);
    }
  }
}
