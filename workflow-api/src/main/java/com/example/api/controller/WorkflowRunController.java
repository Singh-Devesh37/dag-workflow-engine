package com.example.api.controller;

import com.example.api.ApiResponse;
import com.example.core.WorkflowFacade;
import com.example.api.model.StartWorkflowRequest;
import com.example.core.model.WorkflowRun;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workflow-runs")
@RequiredArgsConstructor
public class WorkflowRunController {

    private final WorkflowFacade workflowFacade;

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<String>> startWorkflow(@RequestBody StartWorkflowRequest request) {
        String runId = workflowFacade.startWorkflow(
                request.workflowId(),
                request.tasks(),
                request.initialContext()
        );
        return ResponseEntity.ok(ApiResponse.success("Workflow started", runId));
    }

    @GetMapping("/{runId}")
    public ResponseEntity<ApiResponse<WorkflowRun>> getWorkflowRun(@PathVariable String runId) {
        return workflowFacade.getWorkflowRun(runId)
                .map(run -> ResponseEntity.ok(ApiResponse.success(run)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<WorkflowRun>>> getAllRuns() {
        return ResponseEntity.ok(ApiResponse.success(workflowFacade.getAllRuns()));
    }
}

