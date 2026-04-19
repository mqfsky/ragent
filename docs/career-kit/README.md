# Ragent 求职材料包

这套材料包面向这样的人：

- 工作未满 1 年，想尽快把一个真实 AI 项目消化后写进简历
- 当前主语言不是 Java，但希望转向 `Java + AI Agent / RAG` 岗位
- 目标是在 1 个月内达到“能讲清楚、能演示、能应对一轮面试”的状态

建议学习顺序：

1. 先读 [01-project-map.md](./01-project-map.md)，建立项目地图和讲述框架。
2. 再读 [02-runbook-demo.md](./02-runbook-demo.md)，准备本地启动和演示。
3. 然后读 [03-deep-dives.md](./03-deep-dives.md)，深挖 3 个高价值亮点。
4. 最后读 [04-resume-interview-kit.md](./04-resume-interview-kit.md)，直接沉淀成简历和面试话术。

## 你 1 个月内的最低目标

- 能不看笔记讲清楚项目是什么、解决什么问题、核心链路是什么
- 能画出一条问答链路和一条知识入库链路
- 能解释 3 个亮点：多路检索、模型路由容错、Trace/异步上下文透传
- 能做一次后台演示，或者解释聊天主链路如何工作
- 能写出 2 到 3 条简历 bullet，并回答 10 个高频追问

## 一周一目标

### 第 1 周

- 建立项目全景认知
- 看懂模块职责和前后端页面
- 形成 3 分钟项目介绍稿
- 确认本地依赖和启动路径

### 第 2 周

- 深挖 3 个技术亮点
- 明确每个亮点的“问题-方案-收益”
- 准备每个亮点的高频追问

### 第 3 周

- 做一次轻量实操背书
- 补齐 Java/Spring Boot 相关表达
- 整理 STAR 项目经历

### 第 4 周

- 产出正式简历项目描述
- 产出 30 秒、3 分钟、10 分钟三版话术
- 用模拟问答反复演练

## 你现在最该抓住的卖点

- 不是“我会 Java 很久了”，而是“我能快速迁移技术栈并理解复杂工程系统”
- 不是“我调过大模型 API”，而是“我理解 RAG/Agent 的完整工程链路”
- 不是“我改了多少代码”，而是“我能把核心设计讲清楚，并能结合真实代码说明理由”

## 仓库里的真实锚点

- 主应用入口：`bootstrap/src/main/java/com/nageoffer/ai/ragent/RagentApplication.java`
- MCP 服务入口：`mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/MCPServerApplication.java`
- 前端路由：`frontend/src/router.tsx`
- 聊天接口：`bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/controller/RAGChatController.java`
- 知识库文档接口：`bootstrap/src/main/java/com/nageoffer/ai/ragent/knowledge/controller/KnowledgeDocumentController.java`
- 多通道检索：`bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/MultiChannelRetrievalEngine.java`
- 模型容错：`infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/model/ModelRoutingExecutor.java`
- 链路追踪：`bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/aop/RagTraceAspect.java`

