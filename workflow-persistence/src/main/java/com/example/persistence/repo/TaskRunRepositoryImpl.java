package com.example.persistence.repo;

import com.example.core.enums.RunStatus;
import com.example.core.model.TaskRun;
import com.example.core.repo.TaskRunRepository;
import com.example.persistence.entity.TaskRunEntity;
import com.example.persistence.entity.WorkflowRunEntity;
import com.example.persistence.mapper.WorkflowEntityMapper;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@Transactional
public class TaskRunRepositoryImpl implements TaskRunRepository {

    private final TaskRunJpaRepository taskRunJpaRepository;
    private final WorkflowRunJpaRepository workflowRunJpaRepository;
    private final WorkflowEntityMapper mapper;

    public TaskRunRepositoryImpl(
            TaskRunJpaRepository taskRunJpaRepository,
            WorkflowRunJpaRepository workflowRunJpaRepository,
            WorkflowEntityMapper mapper) {
        this.taskRunJpaRepository = taskRunJpaRepository;
        this.workflowRunJpaRepository = workflowRunJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public TaskRun save(TaskRun taskRun) {
        // Convert TaskRun to TaskRunEntity
        TaskRunEntity taskRunEntity = mapper.toEntity(taskRun);

        // Get or reference the WorkflowRunEntity
        WorkflowRunEntity workflowRunEntity = workflowRunJpaRepository
                .findById(taskRun.getWorkflowId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "WorkflowRun not found with ID: " + taskRun.getWorkflowId()));

        taskRunEntity.setWorkflowRunEntity(workflowRunEntity);

        // Save and convert back
        TaskRunEntity saved = taskRunJpaRepository.save(taskRunEntity);
        return mapper.fromEntity(saved);
    }

    @Override
    public Optional<TaskRun> findById(String id) {
        return taskRunJpaRepository.findById(id)
                .map(mapper::fromEntity);
    }

    @Override
    public List<TaskRun> findByWorkflowId(String workflowId) {
        return taskRunJpaRepository.findByWorkflowId(workflowId).stream()
                .map(mapper::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<TaskRun> findByStatus(RunStatus status) {
        return taskRunJpaRepository.findByStatus(status).stream()
                .map(mapper::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<TaskRun> findAll() {
        return taskRunJpaRepository.findAll().stream()
                .map(mapper::fromEntity)
                .collect(Collectors.toList());
    }

}
