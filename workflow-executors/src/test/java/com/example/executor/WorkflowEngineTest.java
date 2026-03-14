//package com.example.executor;
//
//import com.example.core.model.TaskNode;
//import com.example.core.engine.WorkflowEngine;
//import com.example.core.model.TaskRun;
//import com.example.core.model.WorkflowDefinition;
//
//import java.util.Map;
//
//public class WorkflowEngineTest {
//    public static void main(String[] args){
//        WorkflowDefinition workflow = new WorkflowDefinition("DataPipeline");
//
//
//        TaskNode taskA = new TaskNode("FetchAPI", new RestTaskExecutor("https://api.example.com/data"), 3, 1000, 5000);
//        TaskNode taskB = new TaskNode("TransformData", new CustomLogicTaskExecutor(), 2, 500, 5000);
//        TaskNode taskC = new TaskNode("StoreDB", new CustomLogicTaskExecutor(), 2, 500, 5000);
//        TaskNode taskD = new TaskNode("NotifyService", new RestTaskExecutor("https://api.example.com/notify"), 1, 500, 3000);
//
//// Define dependencies
//        taskB.addDependency(taskA);
//        taskC.addDependency(taskA);
//        taskD.addDependency(taskC);
//        taskD.addDependency(taskB);
//
//        workflow.addTask(taskA);
//        workflow.addTask(taskB);
//        workflow.addTask(taskC);
//        workflow.addTask(taskD);
//
//        WorkflowEngine engine = new WorkflowEngine();
//        Map<String, TaskRun> results =  engine.execute(workflow);
//        System.exit(1);
//    }
//}
