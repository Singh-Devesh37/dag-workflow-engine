package com.example.scheduler.service;

import com.example.core.engine.WorkflowSchedulerService;
import com.example.core.exception.WorkflowSchedulerException;
import com.example.scheduler.job.WorkflowTriggerJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WorkflowSchedulerServiceImpl implements WorkflowSchedulerService {

  private final Scheduler scheduler;

  @Autowired
  public WorkflowSchedulerServiceImpl(SchedulerFactoryBean schedulerFactoryBean) {
    this.scheduler = schedulerFactoryBean.getScheduler();
  }

  public void scheduleWorkflow(String workflowId, String cronExpression, Map<String, Object> ctx) throws WorkflowSchedulerException{
    try {
      JobDataMap jobDataMap = new JobDataMap();
      jobDataMap.put("workflowId", workflowId);
      jobDataMap.put("initialContextJson", new ObjectMapper().writeValueAsString(ctx));
      JobDetail jobDetail =
          JobBuilder.newJob(WorkflowTriggerJob.class)
              .withIdentity("workflow-" + workflowId, "workflow-jobs")
              .usingJobData(jobDataMap)
              .storeDurably()
              .build();
      Trigger trigger =
          TriggerBuilder.newTrigger()
              .withIdentity("trigger-" + workflowId, "workflow-triggers")
              .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
              .forJob(jobDetail)
              .build();

      scheduler.scheduleJob(jobDetail, trigger);
    } catch (Exception e) {
        throw new WorkflowSchedulerException("Unable to schedule Workflow :" + workflowId, e);
    }
  }

  public void unscheduleWorkflow(String workflowId) throws WorkflowSchedulerException{
    try {
      scheduler.deleteJob(new JobKey("workflow-" + workflowId, "workflow-jobs"));
    } catch (SchedulerException e) {
        throw new WorkflowSchedulerException("Unable to unschedule job: "+ workflowId, e);
    }
  }

  public List<String> listScheduledJobs() throws WorkflowSchedulerException{
    try {
      List<String> jobs = new ArrayList<>();
      for (String groupName : scheduler.getJobGroupNames()) {
        for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName))) {
          jobs.add(jobKey.getName());
        }
      }
      return jobs;
    } catch (SchedulerException e) {
        throw new WorkflowSchedulerException("Unable to fetch all scheduled jobs", e);
    }
  }
}
