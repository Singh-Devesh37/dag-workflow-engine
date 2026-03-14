package com.example.core.engine;

import com.example.core.exception.WorkflowSchedulerException;

import java.util.List;
import java.util.Map;

public interface WorkflowSchedulerService {
    public void scheduleWorkflow(String workflowId, String cronExpression, Map<String, Object> ctx) throws WorkflowSchedulerException;

    public void unscheduleWorkflow(String workflowId) throws WorkflowSchedulerException;

    public List<String> listScheduledJobs() throws WorkflowSchedulerException;


}
