package com.example.persistence.repo;

import com.example.core.enums.RunStatus;
import com.example.core.model.WorkflowRun;
import com.example.core.repo.WorkflowRunRepository;
import com.example.persistence.mapper.WorkflowEntityMapper;
import com.example.persistence.entity.WorkflowRunEntity;

import java.util.List;
import java.util.Optional;

public class WorkflowRunRepositoryImpl implements WorkflowRunRepository {

    private final  WorkflowRunJpaRepository workflowRunJpaRepository;
    private final WorkflowEntityMapper mapper;

    public WorkflowRunRepositoryImpl(WorkflowRunJpaRepository workflowRunJpaRepository, WorkflowEntityMapper mapper){
        this.workflowRunJpaRepository = workflowRunJpaRepository;
        this.mapper = mapper;
    }
    @Override
    public WorkflowRun save(WorkflowRun workflowRun) {
        WorkflowRunEntity entity = mapper.toEntity(workflowRun);
        WorkflowRunEntity savedEntity = workflowRunJpaRepository.save(entity);
        return mapper.fromEntity(savedEntity);
    }

    @Override
    public Optional<WorkflowRun> findById(String id) {
        return workflowRunJpaRepository.findById(id).map(mapper::fromEntity);
    }

    @Override
    public List<WorkflowRun> findByStatus(RunStatus status) {
        return List.of();
    }

    @Override
    public List<WorkflowRun> findAll() {
        return workflowRunJpaRepository.findAll().stream().map(mapper::fromEntity).toList();
    }
}
