package io.mint.ai.agent.controller;

import io.mint.ai.agent.model.CreateRunRequest;
import io.mint.ai.agent.model.CreateRunResponse;
import io.mint.ai.agent.model.RunCancelResponse;
import io.mint.ai.agent.model.RunHistoryResponse;
import io.mint.ai.agent.model.RunStatusResponse;
import io.mint.ai.agent.service.RunExecutionService;
import io.mint.ai.agent.runtime.RunEventBus;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/agent/runs")
public class RunController {

    private final RunExecutionService runExecutionService;
    private final RunEventBus runEventBus;

    public RunController(RunExecutionService runExecutionService, RunEventBus runEventBus) {
        this.runExecutionService = runExecutionService;
        this.runEventBus = runEventBus;
    }

    @PostMapping
    public CreateRunResponse createRun(@RequestBody CreateRunRequest request) {
        try {
            return runExecutionService.startRun(request);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping(value = "/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(@PathVariable String runId) {
        if (runExecutionService.getStatus(runId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "runId not found");
        }
        return runEventBus.subscribe(runId);
    }

    @PostMapping("/{runId}/cancel")
    public RunCancelResponse cancelRun(@PathVariable String runId) {
        boolean found = runExecutionService.cancelRun(runId);
        if (!found) {
            if (!runExecutionService.exists(runId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "runId not found");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "run is already in terminal state");
        }
        return new RunCancelResponse(runId, "cancelling");
    }

    @GetMapping("/{runId}")
    public RunStatusResponse getRun(@PathVariable String runId) {
        RunStatusResponse status = runExecutionService.getStatus(runId);
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "runId not found");
        }
        return status;
    }

    @GetMapping
    public RunHistoryResponse listRuns(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return runExecutionService.listRuns(status, keyword, page, size);
    }
}
