package io.mint.ai.agent.controller;

import io.mint.ai.agent.model.GenerateRequest;
import io.mint.ai.agent.model.GenerateResult;
import io.mint.ai.agent.orchestrator.GenerateOrchestrator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GenerateController {

    private final GenerateOrchestrator generateOrchestrator;

    public GenerateController(GenerateOrchestrator generateOrchestrator) {
        this.generateOrchestrator = generateOrchestrator;
    }

    @PostMapping("/generate")
    public GenerateResult generate(@RequestBody GenerateRequest request) {
        return generateOrchestrator.generate(request);
    }
}
