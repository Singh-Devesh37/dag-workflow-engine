package com.example.api.controller;

import com.example.api.app.WorkflowRunAO;
import com.example.core.WorkflowFacade;
import com.example.api.mapper.WorkflowResultMapper;
import com.example.api.model.ScheduleWorkflowRequest;
import com.example.api.model.StartWorkflowRequest;
import com.example.api.model.WorkflowRunResponse;
import com.example.api.model.WorkflowScheduleResponse;
import com.example.core.model.WorkflowRun;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowController {

  private final WorkflowFacade workflowFacade;
  private final WorkflowResultMapper workflowResultMapper;

  @PostMapping("/start")
  public ResponseEntity<WorkflowRunResponse> startWorkflow(
      @RequestBody StartWorkflowRequest request) {
    try {
      String runId =
          workflowFacade.startWorkflow(
              request.workflowId(), request.tasks(), request.initialContext());
      return ResponseEntity.ok(new WorkflowRunResponse(runId, "RUNNING", "Workflow Initiated"));
    } catch (Exception e) {
      return ResponseEntity.badRequest()
          .body(new WorkflowRunResponse(null, "FAILED", e.getMessage()));
    }
  }

  @PostMapping("/schedule")
  public ResponseEntity<WorkflowScheduleResponse> scheduleWorkflow(
      @RequestBody ScheduleWorkflowRequest request) {
    try {
      workflowFacade.scheduleWorkflow(
          request.workflowId(), request.initialContext(), request.cron());
      return ResponseEntity.ok(
          new WorkflowScheduleResponse("PENDING", "Workflow Scheduled Successfully"));
    } catch (Exception e) {
      return ResponseEntity.badRequest()
          .body(new WorkflowScheduleResponse("FAILED", e.getMessage()));
    }
  }

  @GetMapping("/runs")
    public ResponseEntity<List<WorkflowRunAO>> getAllWorkflows(){
      List<WorkflowRun> workflowRuns = workflowFacade.getAllRuns();
      return ResponseEntity.ok(workflowRuns.stream().map(workflowResultMapper::toAO).toList());
  }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<WorkflowRunAO> getWorkflowRun(@PathVariable String runId){
        return workflowFacade.getWorkflowRun(runId)
                .map(workflowResultMapper::toAO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
