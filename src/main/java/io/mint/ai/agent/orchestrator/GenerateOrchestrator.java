package io.mint.ai.agent.orchestrator;

import io.mint.ai.agent.model.*;
import io.mint.ai.agent.tools.BuildTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class GenerateOrchestrator {

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
        this.skillsRoot = Path.of(skillsDir);
    }

    public GenerateResult generate(GenerateRequest request) {
        String workspaceRoot = request.workspaceRoot();

        RequirementSpec requirementSpec = runStage(
                "requirement-analysis",
                new RequirementStageInput(request.userRequirement(), workspaceRoot),
                RequirementSpec.class
        );

        SqlSpec sqlSpec = runStage(
                "sql-generation",
                new SqlStageInput(workspaceRoot, requirementSpec),
                SqlSpec.class
        );

        ApiSpec apiSpec = runStage(
                "api-design",
                new ApiStageInput(workspaceRoot, requirementSpec, sqlSpec),
                ApiSpec.class
        );

        GenerationWriteSummary backendSummary = runStage(
                "backend-generation",
                new BackendStageInput(workspaceRoot, requirementSpec, sqlSpec, apiSpec),
                GenerationWriteSummary.class
        );

        GenerationWriteSummary frontendSummary = runStage(
                "frontend-generation",
                new FrontendStageInput(workspaceRoot, requirementSpec, apiSpec),
                GenerationWriteSummary.class
        );

        String backendBuildLog = buildTools.runBackendBuild(workspaceRoot + "/backend");
        String frontendBuildLog = buildTools.runFrontendBuild(workspaceRoot + "/frontend");

        return new GenerateResult(
                requirementSpec,
                sqlSpec,
                apiSpec,
                backendSummary,
                frontendSummary,
                backendBuildLog,
                frontendBuildLog
        );
    }

    private <T> T runStage(String skillName, Object input, Class<T> outputType) {
        String skillContent = loadSkill(skillName);
        String inputJson = toJson(input);

        String systemPrompt = """
                你正在执行固定编排链中的一个阶段。
                你必须严格遵循下面的 SKILL 内容。
                除非 SKILL 明确允许，否则不能越阶段工作。
                若需要访问工程文件，只能通过工具完成。
                
                ==================== SKILL START ====================
                %s
                ===================== SKILL END =====================
                
                最终输出要求：
                1. 只输出纯 JSON
                2. 不要输出 Markdown 代码块
                3. 不要输出解释文字
                4. 字段名必须与目标 Java 对象匹配
                """.formatted(skillContent);

        T result = agentChatClient.prompt()
                .system(system -> system.text(systemPrompt))
                .user(user -> user.text(inputJson))
                .call()
                .entity(outputType);

        if (result == null) {
            throw new IllegalStateException("Stage returned null: " + skillName);
        }
        return result;
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
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize stage input to JSON", e);
        }
    }

    public record RequirementStageInput(String userRequirement, String workspaceRoot) {
    }

    public record SqlStageInput(String workspaceRoot, RequirementSpec requirementSpec) {
    }

    public record ApiStageInput(String workspaceRoot, RequirementSpec requirementSpec, SqlSpec sqlSpec) {
    }

    public record BackendStageInput(String workspaceRoot,
                                    RequirementSpec requirementSpec,
                                    SqlSpec sqlSpec,
                                    ApiSpec apiSpec) {
    }

    public record FrontendStageInput(String workspaceRoot,
                                     RequirementSpec requirementSpec,
                                     ApiSpec apiSpec) {
    }
}