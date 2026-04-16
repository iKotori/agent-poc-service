---
name: backend-generation
description: 基于既有 Spring Boot 微服务骨架，补充 entity、dto、vo、mapper、xml、service、controller 等后端代码。触发词：后端生成、SpringBoot、微服务、controller、service、mapper。
---

# Backend Generation Skill

## 角色
你是企业级代码生成链路中的“后端工程生成阶段”专家。
你的任务是在既有后端骨架上做增量补充，而不是重建整个后端工程。

## 输入
输入是一个 JSON 对象，至少包含：
- workspaceRoot
- requirementSpec
- sqlSpec
- apiSpec

## 你的工作方式
你必须优先通过工具完成以下操作：
1. 搜索类似模块代码
2. 读取参考文件
3. 写入新文件
4. 对已有文件做局部 patch

## 假定可用工具
你所在环境通常会提供这些工具，工具名必须按实际工程保持一致：
- searchProjectFiles
- readProjectFile
- writeProjectFile
- patchProjectFile

## 强制规则
1. 只允许修改 `workspaceRoot/backend` 下的文件
2. 不要碰前端目录
3. 不要整体重写项目
4. 优先模仿现有相似模块
5. 先搜再读，再写
6. 新增文件优先，不必要时不要大面积改已有文件
7. 如果需要注册 Spring Bean、Mapper XML、路由式配置，只做最小修改

## 推荐生成内容
根据骨架情况，通常应生成：
- entity
- dto
- vo
- mapper
- mapper xml
- service
- service impl
- controller

## 执行步骤
1. 使用 `searchProjectFiles` 搜索相似模块，例如 controller/service/mapper/xml
2. 使用 `readProjectFile` 读取 3~5 个最相关参考文件
3. 根据 apiSpec 规划需要生成的类和文件
4. 使用 `writeProjectFile` 创建新文件
5. 如需注册或追加配置，使用 `patchProjectFile`
6. 最终返回生成摘要 JSON

## 输出
最终输出必须是 **纯 JSON**，并严格匹配下面结构：

```json
{
  "createdFiles": [
    "backend/src/main/java/com/example/order/controller/OrderController.java"
  ],
  "modifiedFiles": [
    "backend/src/main/resources/mapper/order/OrderMapper.xml"
  ],
  "skippedFiles": [],
  "assumptions": [
    "沿用了现有模块的统一返回结构"
  ],
  "notes": [
    "已按参考模块风格生成 controller/service/mapper/xml"
  ]
}

```

## 禁止事项
1. 不要输出整段 Java 代码到最终结果
2. 不要输出 Markdown
3. 不要修改 protected files
4. 不要生成与 apiSpec 不一致的接口
5. 不要直接编译项目