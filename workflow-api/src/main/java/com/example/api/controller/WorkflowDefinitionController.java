package com.example.api.controller;

import com.example.api.app.WorkflowDefinitionAO;
import com.example.core.WorkflowFacade;
import com.example.api.mapper.WorkflowDefinitionMapper;
import com.example.core.model.WorkflowDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workflow-definitions")
@RequiredArgsConstructor
public class WorkflowDefinitionController {
    private final WorkflowFacade workflowFacade;
    private final WorkflowDefinitionMapper mapper;

    @PostMapping
    public ResponseEntity<WorkflowDefinitionAO> create(@RequestBody WorkflowDefinitionAO def) {
        WorkflowDefinition definition = mapper.fromAO(def);
        WorkflowDefinition savedDefinition = workflowFacade.saveWorkflowDefinition(definition);
        WorkflowDefinitionAO savedAO = mapper.toAO(savedDefinition);
        return ResponseEntity.ok(savedAO);
    }

    @GetMapping("/definitions/{name}")
    public ResponseEntity<WorkflowDefinitionAO> get(@PathVariable String name) {
        return workflowFacade.getWorkflowDefinition(name)
                .map(mapper::toAO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/definitions")
    public ResponseEntity<List<WorkflowDefinitionAO>> list() {
        List<WorkflowDefinition> workflowDefinitions = workflowFacade.getAllWorkflowDefinitions();
        return ResponseEntity.ok(workflowDefinitions.stream().map(mapper::toAO).toList());
    }

}
