package io.mint.ai.agent.orchestrator;

import io.mint.ai.agent.model.GenerateResult;

import java.util.List;

public interface OrchestratorEventListener {

    default void onRunStarted(String workspaceRoot, List<String> stages) {}

    default void onStageStarted(String stage, int index, int total) {}

    default void onStageCompleted(String stage, String artifact, long durationMs) {}

    default void onBuildLog(String target, String line) {}

    default void onRunCompleted(GenerateResult result, String artifactsDir) {}

    default void onRunError(String stage, Exception exception) {}

    default void onRunCancelled() {}
}
