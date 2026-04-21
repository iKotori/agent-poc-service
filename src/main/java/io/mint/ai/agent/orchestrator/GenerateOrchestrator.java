package io.mint.ai.agent.orchestrator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mint.ai.agent.model.*;
import io.mint.ai.agent.tools.BuildTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

@Service
public class GenerateOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(GenerateOrchestrator.class);
    private static final List<String> STAGES = List.of(
            "requirement-analysis",
            "sql-generation",
            "api-design",
            "backend-generation",
            "frontend-generation",
            "backend-build",
            "frontend-build"
    );

    private final ChatClient agentChatClient;
    private final BuildTools buildTools;
    private final ObjectMapper objectMapper;
    private final Path skillsRoot;

    public GenerateOrchestrator(ChatClient agentChatClient,
                                BuildTools buildTools,
                                ObjectMapper objectMapper,
                                @Value("${app.agent.skills-dir:./skills}") String skillsDir) {
        this.agentChatClient = agentChatClient;
        this.buildTools = buildTools;
        this.objectMapper = objectMapper;
        this.skillsRoot = Path.of(skillsDir).toAbsolutePath().normalize();
    }

    public GenerateResult generate(GenerateRequest request) {
        return doGenerate(request, new OrchestratorEventListener() {}, () -> false);
    }

    public GenerateResult generateStreaming(GenerateRequest request,
                                            OrchestratorEventListener listener,
                                            BooleanSupplier isCancelled) {
        return doGenerate(request, listener, isCancelled);
    }

    private GenerateResult doGenerate(GenerateRequest request,
                                      OrchestratorEventListener listener,
                                      BooleanSupplier isCancelled) {
        String workspaceRoot = request.workspaceRoot();
        Path artifactDir = prepareArtifactDir(workspaceRoot);

        listener.onRunStarted(workspaceRoot, STAGES);
        log.info("[generate] START, workspaceRoot={}", workspaceRoot);

        RequirementSpec requirementSpec = executeStage(
                "requirement-analysis",
                1,
                listener,
                isCancelled,
                () -> runStage(
                        "requirement-analysis",
                        new RequirementStageInput(request.userRequirement(), workspaceRoot),
                        RequirementSpec.class,
                        artifactDir
                )
        );

        SqlSpec sqlSpec = executeStage(
                "sql-generation",
                2,
                listener,
                isCancelled,
                () -> runStage(
                        "sql-generation",
                        new SqlStageInput(workspaceRoot, requirementSpec),
                        SqlSpec.class,
                        artifactDir
                )
        );

        ApiSpec apiSpec = executeStage(
                "api-design",
                3,
                listener,
                isCancelled,
                () -> runStage(
                        "api-design",
                        new ApiStageInput(workspaceRoot, requirementSpec, sqlSpec),
                        ApiSpec.class,
                        artifactDir
                )
        );

        GenerationWriteSummary backendSummary = executeStage(
                "backend-generation",
                4,
                listener,
                isCancelled,
                () -> runStage(
                        "backend-generation",
                        new BackendStageInput(workspaceRoot, requirementSpec, sqlSpec, apiSpec),
                        GenerationWriteSummary.class,
                        artifactDir
                )
        );

        GenerationWriteSummary frontendSummary = executeStage(
                "frontend-generation",
                5,
                listener,
                isCancelled,
                () -> runStage(
                        "frontend-generation",
                        new FrontendStageInput(workspaceRoot, requirementSpec, apiSpec),
                        GenerationWriteSummary.class,
                        artifactDir
                )
        );

        ensureNotCancelled(isCancelled);
        listener.onStageStarted("backend-build", 6, STAGES.size());
        log.info("[generate] backend build start");
        long backendBuildStartedAt = System.currentTimeMillis();
        String backendBuildLog = buildTools.runBackendBuild(
                workspaceRoot + "/backend",
                line -> listener.onBuildLog("backend", line),
                isCancelled
        );
        writeTextArtifact(artifactDir, "backend-build.log", backendBuildLog);
        listener.onStageCompleted("backend-build", "backend-build.log", System.currentTimeMillis() - backendBuildStartedAt);

        ensureNotCancelled(isCancelled);
        listener.onStageStarted("frontend-build", 7, STAGES.size());
        log.info("[generate] frontend build start");
        long frontendBuildStartedAt = System.currentTimeMillis();
        String frontendBuildLog = buildTools.runFrontendBuild(
                workspaceRoot + "/frontend",
                line -> listener.onBuildLog("frontend", line),
                isCancelled
        );
        writeTextArtifact(artifactDir, "frontend-build.log", frontendBuildLog);
        listener.onStageCompleted("frontend-build", "frontend-build.log", System.currentTimeMillis() - frontendBuildStartedAt);

        ensureNotCancelled(isCancelled);
        log.info("[generate] DONE");

        GenerateResult result = new GenerateResult(
                requirementSpec,
                sqlSpec,
                apiSpec,
                backendSummary,
                frontendSummary,
                backendBuildLog,
                frontendBuildLog
        );
        listener.onRunCompleted(result, artifactDir.toString());
        return result;
    }

    private <T> T executeStage(String stageName,
                               int stageIndex,
                               OrchestratorEventListener listener,
                               BooleanSupplier isCancelled,
                               StageSupplier<T> supplier) {
        ensureNotCancelled(isCancelled);
        listener.onStageStarted(stageName, stageIndex, STAGES.size());
        long startedAt = System.currentTimeMillis();
        try {
            T result = supplier.get();
            listener.onStageCompleted(stageName, stageName + "-parsed.json", System.currentTimeMillis() - startedAt);
            return result;
        } catch (Exception ex) {
            listener.onRunError(stageName, ex);
            throw ex;
        }
    }

    private <T> T runStage(String skillName, Object input, Class<T> outputType, Path artifactDir) {
        log.info("[stage:{}] START", skillName);

        String skillContent = loadSkill(skillName);
        String inputJson = toJson(input);

        writeTextArtifact(artifactDir, skillName + "-input.json", inputJson);

        String systemPrompt = """
                你正在执行固定编排链中的一个阶段。
                你必须严格遵循下面的 SKILL 内容。
                除非 SKILL 明确允许，否则不能越阶段工作。
                若需要访问工程文件，只能通过以下工具完成：
                - listDirectory
                - searchProjectFiles
                - readProjectFile
                - writeProjectFile
                - patchProjectFile
                不要调用任何其他工具名。

                ==================== SKILL START ====================
                %s
                ===================== SKILL END =====================

                最终输出要求：
                1. 只输出纯 JSON
                2. 不要输出 Markdown 代码块
                3. 不要输出解释文字
                4. 字段名必须与目标 Java 对象匹配
                """.formatted(skillContent);

        try {
            String raw = agentChatClient.prompt()
                    .system(system -> system.text(systemPrompt))
                    .user(user -> user.text(inputJson))
                    .call()
                    .content();

            log.info("[stage:{}] RAW RESPONSE: {}", skillName, abbreviate(raw, 2000));
            writeTextArtifact(artifactDir, skillName + "-raw.json", raw);

            T result = objectMapper.readValue(raw, outputType);
            writeArtifact(artifactDir, skillName + "-parsed.json", result);

            log.info("[stage:{}] SUCCESS", skillName);
            return result;
        } catch (Exception e) {
            log.error("[stage:{}] FAILED", skillName, e);
            throw new IllegalStateException("Stage failed: " + skillName, e);
        }
    }

    private void ensureNotCancelled(BooleanSupplier isCancelled) {
        if (isCancelled.getAsBoolean()) {
            throw new CancellationException("Run cancelled");
        }
    }

    private String loadSkill(String skillName) {
        Path path = skillsRoot.resolve(skillName).resolve("SKILL.md");
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load skill file: " + path, e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize stage input to JSON", e);
        }
    }

    private Path prepareArtifactDir(String workspaceRoot) {
        try {
            Path dir = Path.of(workspaceRoot).toAbsolutePath().normalize().resolve("artifacts");
            Files.createDirectories(dir);
            return dir;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare artifact directory", e);
        }
    }

    private void writeArtifact(Path artifactDir, String fileName, Object value) {
        try {
            Path file = artifactDir.resolve(fileName);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write artifact: " + fileName, e);
        }
    }

    private void writeTextArtifact(Path artifactDir, String fileName, String text) {
        try {
            Path file = artifactDir.resolve(fileName);
            Files.writeString(file, text == null ? "" : text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write text artifact: " + fileName, e);
        }
    }

    private String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    public record RequirementStageInput(String userRequirement, String workspaceRoot) {}
    public record SqlStageInput(String workspaceRoot, RequirementSpec requirementSpec) {}
    public record ApiStageInput(String workspaceRoot, RequirementSpec requirementSpec, SqlSpec sqlSpec) {}
    public record BackendStageInput(String workspaceRoot, RequirementSpec requirementSpec, SqlSpec sqlSpec, ApiSpec apiSpec) {}
    public record FrontendStageInput(String workspaceRoot, RequirementSpec requirementSpec, ApiSpec apiSpec) {}

    @FunctionalInterface
    private interface StageSupplier<T> {
        T get();
    }
}
