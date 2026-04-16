package io.mint.ai.agent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class ProjectReadTools {

    @Tool(description = "在当前工作区搜索相关文件，返回文件路径和摘要")
    public String searchProjectFiles(String keyword) {
        return "...";
    }

    @Tool(description = "读取当前工作区指定文件内容")
    public String readProjectFile(String path) {
        return "...";
    }
}
