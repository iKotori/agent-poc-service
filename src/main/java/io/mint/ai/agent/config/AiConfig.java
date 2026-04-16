package io.mint.ai.agent.config;

import io.mint.ai.agent.tools.ProjectReadTools;
import io.mint.ai.agent.tools.ProjectWriteTools;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AiConfig {

    @Bean
    public ChatClient agentChatClient(ChatClient.Builder chatClientBuilder,
                                      ProjectReadTools projectReadTools,
                                      ProjectWriteTools projectWriteTools,
                                      @Value("${app.agent.skills-dir:./skills}") String skillsDir) {

        return chatClientBuilder
                .defaultSystem(system -> system.text("""
                        你是企业级代码生成编排助手。
                        你必须在用户提供的固定工程骨架上做“增量补充”，而不是重建整个项目。
                        任何代码搜索、文件读取、文件写入、文件修改都必须通过工具完成。
                        如果当前阶段要求输出 JSON，则最终输出必须是纯 JSON，不能带 Markdown 代码块。
                        """))
                .defaultToolCallbacks(
                        SkillsTool.builder()
                                .addSkillsDirectory(skillsDir)
                                .build()
                )
                .defaultTools(projectReadTools, projectWriteTools)
                .build();
    }
}