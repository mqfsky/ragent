# Day 14 缓存、消息队列、调度与 MCP

今天收尾，补上项目里最像“企业工程增强项”的部分。

## 今天看哪些文件

- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/intent/IntentTreeCacheManager.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/intent/IntentTreeCacheManager.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/aop/ChatQueueLimiter.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/aop/ChatQueueLimiter.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/knowledge/mq/KnowledgeDocumentChunkConsumer.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/knowledge/mq/KnowledgeDocumentChunkConsumer.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/mq/MessageFeedbackConsumer.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/mq/MessageFeedbackConsumer.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/knowledge/schedule/KnowledgeDocumentScheduleJob.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/knowledge/schedule/KnowledgeDocumentScheduleJob.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/mcp/MCPToolRegistry.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/mcp/MCPToolRegistry.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/mcp/client/HttpMCPClient.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/mcp/client/HttpMCPClient.java)
- [mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/endpoint/MCPDispatcher.java](/Users/mqf/project/ragent/mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/endpoint/MCPDispatcher.java)
- [mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/executor/WeatherMCPExecutor.java](/Users/mqf/project/ragent/mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/executor/WeatherMCPExecutor.java)

## 今天重点看什么

### 1. Redis 和缓存

重点理解：

- 什么数据适合缓存
- 为什么意图树适合放缓存

### 2. 限流与排队

重点理解：

- 为什么聊天接口要做并发控制
- 为什么 AI 服务常常需要排队和限流

### 3. RocketMQ

重点理解：

- 为什么文档处理适合异步
- Consumer 在整个系统里扮演什么角色

### 4. 定时任务

重点理解：

- 知识文档为什么需要定时扫描和刷新

### 5. MCP

重点理解：

- MCP 和传统知识检索不一样
- MCP 更像“模型调用工具”

## 今天的输出

请自己总结一张表：

- 组件
- 它解决的问题
- 在项目中的一个具体类

建议至少写这几个：

- Redis
- Redisson
- RocketMQ
- Scheduled
- MCP

## 打卡检查

你今天应该能回答：

- 为什么这个项目比普通 Demo 更像企业项目？
- MCP 和知识库检索最大的差别是什么？

## 收官目标

如果 14 天都完成了，你至少应该已经能独立讲清楚：

- 聊天主链路
- 检索主链路
- 入库主链路
- 核心中间件作用
- 模型调用与工具调用的大致边界

## 参考答案提纲

### 1. 为什么这个项目比普通 Demo 更像企业项目

- 有完整的知识库管理和入库链路
- 有缓存、限流、异步消息、定时刷新等工程能力
- 有模型路由、向量检索、流式输出、工具调用等完整链路

### 2. MCP 和知识库检索最大的差别是什么

- 知识库检索是“找已有知识片段”
- MCP 更像“调用外部工具获取动态数据”

### 3. 各组件解决的问题

- Redis：缓存和状态辅助
- Redisson：锁、信号量、并发控制
- RocketMQ：异步解耦
- Scheduled：定时执行后台任务
- MCP：给模型提供工具调用能力

### 4. 最终应该能讲清的 5 个问题

- 一个聊天请求怎么流转
- RAG 为什么要检索
- 文档为什么要入库流水线
- 中间件分别解决什么问题
- 模型调用和工具调用什么时候各自发挥作用
