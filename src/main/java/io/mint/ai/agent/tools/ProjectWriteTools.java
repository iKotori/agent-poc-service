package io.mint.ai.agent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class ProjectWriteTools {

    @Tool(description = "写入当前工作区文件，若文件不存在则创建")
    public String writeProjectFile(String path, String content) {
        return "...";
    }

    @Tool(description = "对当前工作区文件做局部补丁修改")
    public String patchProjectFile(String path, String patch) {
        return "...";
    }
}
