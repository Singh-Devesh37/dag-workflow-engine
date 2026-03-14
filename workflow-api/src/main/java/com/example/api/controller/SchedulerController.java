package com.example.api.controller;

import com.example.core.engine.WorkflowSchedulerService;
import com.example.core.exception.WorkflowSchedulerException;
import com.example.scheduler.model.WorkflowScheduleRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/schedules")
public class SchedulerController {

    @Autowired
    private WorkflowSchedulerService schedulerService;

    @PostMapping
    public ResponseEntity<String> createSchedule(@RequestBody WorkflowScheduleRequest req) {
        try{
            schedulerService.scheduleWorkflow(req.workflowId(),req.cronExpression(), req.context());
            return ResponseEntity.ok("Workflow " + req.workflowId() + " Scheduled");
        } catch(WorkflowSchedulerException e){
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<String>> listSchedules(){
        try{
            List<String> scheduledJobs = schedulerService.listScheduledJobs();
            return ResponseEntity.ok(scheduledJobs);
        }catch (WorkflowSchedulerException e){
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{workflowId}")
    public ResponseEntity<String> deleteSchedule(@PathVariable String workflowId){
        try {
            schedulerService.unscheduleWorkflow(workflowId);
            return ResponseEntity.ok("Deleted " + workflowId);
        } catch (WorkflowSchedulerException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }

    }

}
