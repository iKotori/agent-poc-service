package io.mint.ai.agent.controller;

import io.mint.ai.agent.model.GenerateRequest;
import io.mint.ai.agent.model.GenerateResult;
import io.mint.ai.agent.orchestrator.GenerateOrchestrator;
import io.mint.ai.agent.service.WorkspaceService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/agent")
public class GenerateController {

    private final GenerateOrchestrator generateOrchestrator;
    private final WorkspaceService workspaceService;

    public GenerateController(GenerateOrchestrator generateOrchestrator,
                              WorkspaceService workspaceService) {
        this.generateOrchestrator = generateOrchestrator;
        this.workspaceService = workspaceService;
    }

    @PostMapping("/generate")
    public GenerateResult generate(@RequestBody GenerateRequest request) {
        String taskId = "task-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String workspaceRoot = workspaceService.prepareWorkspace(taskId, request.workspaceRoot());

        GenerateRequest actualRequest = new GenerateRequest(
                request.userRequirement(),
                workspaceRoot
        );

        return generateOrchestrator.generate(actualRequest);
    }

    @GetMapping("/ping")
    public String ping() {
        return "ok";
    }
}