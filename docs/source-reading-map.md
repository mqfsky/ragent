# Ragent Source Reading Map

这份文档是给“只掌握 Java 基础语法，正在尝试看懂 Ragent 项目”的同学准备的源码阅读地图。

目标不是一次读完整个项目，而是先建立一条清晰主线，让你知道：

- 这个项目由哪些模块组成
- 一次请求是怎么流转的
- 每一块功能应该从哪里开始看
- 哪些中间件必须先理解，哪些可以后补

## 阅读原则

阅读源码时，不要一上来钻实现细节。每看一个类，先回答 3 个问题：

1. 这个类负责什么
2. 它调用了谁
3. 它的数据从哪里来，最后写到哪里去

建议始终按这个顺序阅读：

1. 先看入口
2. 再看主链路
3. 再看底层支撑
4. 最后看异步、调度、扩展点

## 项目整体结构

Ragent 是一个前后端分离的 Spring Boot 项目，后端按职责分成四个 Maven 模块：

- `bootstrap`
  业务主模块，Controller、Service、RAG 主链路、知识库、入库流水线都在这里
- `framework`
  通用基础设施，包含 Redis、MQ、Web、幂等等公共能力
- `infra-ai`
  AI 基础设施层，负责模型调用、Embedding、Rerank、路由和容错
- `mcp-server`
  MCP 工具服务模块，向主系统暴露可调用工具

建议先从这几个文件建立全局认知：

- [README.md](/Users/mqf/project/ragent/README.md)
- [pom.xml](/Users/mqf/project/ragent/pom.xml)
- [bootstrap/src/main/resources/application.yaml](/Users/mqf/project/ragent/bootstrap/src/main/resources/application.yaml)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/RagentApplication.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/RagentApplication.java)

看这几个文件时，重点不是记住所有配置，而是回答这两个问题：

- 项目依赖了哪些外部组件
- 哪些模块负责核心业务

## 阶段一：先建立“普通 Spring Boot 项目”的阅读能力

如果你现在只有 Java 基础，最先要看懂的不是 RAG，而是这个项目作为普通后端系统的基本写法。

### 推荐阅读顺序

- [bootstrap/src/main/java/com/nageoffer/ai/ragent/user/controller/AuthController.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/user/controller/AuthController.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/user/service/impl/AuthServiceImpl.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/user/service/impl/AuthServiceImpl.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/user/controller/UserController.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/user/controller/UserController.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/user/config/SaTokenConfig.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/user/config/SaTokenConfig.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/user/config/UserContextInterceptor.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/user/config/UserContextInterceptor.java)

### 这一阶段要看懂什么

- 登录接口如何接收请求
- Service 如何处理登录逻辑
- `Sa-Token` 如何完成登录态管理
- 当前用户信息如何在后续请求里被拿到

### 这一阶段你要记的笔记

- 什么是 `Controller`
- 什么是 `Service`
- 什么是拦截器
- 什么是登录态
- 什么是权限校验

如果这一阶段你能读通，后面大部分业务代码都会顺很多。

## 阶段二：看清数据库和业务实体的对应关系

接下来要把“接口层”和“数据库层”连起来。

推荐从知识库模块入手，因为它比 RAG 主链路更接近传统业务开发。

### 推荐阅读顺序

- [bootstrap/src/main/java/com/nageoffer/ai/ragent/knowledge/controller/KnowledgeBaseController.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/knowledge/controller/KnowledgeBaseController.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/knowledge/service/impl/KnowledgeBaseServiceImpl.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/knowledge/service/impl/KnowledgeBaseServiceImpl.java)
- [resources/database/schema_pg.sql](/Users/mqf/project/ragent/resources/database/schema_pg.sql)

如果你想继续往下追，可以继续顺着看：

- `knowledge/dao/entity`
- `knowledge/dao/mapper`

### 这一阶段要看懂什么

- 一个请求最终改了哪些表
- DO、Mapper、Service 之间是什么关系
- 业务对象是怎么落到数据库里的

### 阅读方法

不要试图一口气看完整个 SQL 文件。只要在你阅读某个业务时，去找到它对应的那张表即可。

例如：

- 知识库管理，对应 `t_knowledge_base`
- 文档管理，对应 `t_knowledge_document`
- 分块数据，对应 `t_knowledge_chunk`

## 阶段三：进入聊天主链路

这是整个项目最核心的一段。建议你把它当成当前阅读重点。

### 推荐阅读顺序

- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/controller/RAGChatController.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/controller/RAGChatController.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/RAGChatService.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/RAGChatService.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/impl/RAGChatServiceImpl.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/impl/RAGChatServiceImpl.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/handler/StreamChatEventHandler.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/handler/StreamChatEventHandler.java)
- [framework/src/main/java/com/nageoffer/ai/ragent/framework/web/SseEmitterSender.java](/Users/mqf/project/ragent/framework/src/main/java/com/nageoffer/ai/ragent/framework/web/SseEmitterSender.java)

### 这一阶段要看懂什么

- 聊天接口是怎么进入系统的
- 为什么返回值是 `SseEmitter`
- 为什么聊天结果是流式输出
- 流式事件是怎么一段一段推给前端的

### 你可以这样理解

这一段先不要把它想得太复杂。你可以先把它当成：

- 前端发来一个问题
- 后端开始处理
- 后端不是等全部结果生成完才返回
- 而是边生成边往前端推送

只要把这个骨架理解了，后面再补 AI 细节就会轻松很多。

## 阶段四：看 RAG 检索核心

这是聊天系统和普通聊天机器人拉开差距的地方。

### 推荐阅读顺序

- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/RetrievalEngine.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/RetrievalEngine.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/RetrieverService.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/RetrieverService.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/PgRetrieverService.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/PgRetrieverService.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/MilvusRetrieverService.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/MilvusRetrieverService.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/channel/VectorGlobalSearchChannel.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/channel/VectorGlobalSearchChannel.java)

### 这一阶段要看懂什么

- 用户问题不会直接送给模型
- 系统会先去知识库里检索相关内容
- 检索结果会作为上下文提供给模型
- 这样做的目的，是减少胡编乱造，提高回答和知识库的一致性

### 当前不用死磕的点

如果你现在还不懂向量检索、Embedding、余弦相似度，没有关系。先把它理解成“更智能的相似内容搜索”即可。

## 阶段五：看 Prompt 组装和会话记忆

这一步决定模型最后“看到什么”。

### 推荐阅读顺序

- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/prompt/RAGPromptService.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/prompt/RAGPromptService.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/prompt/DefaultContextFormatter.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/prompt/DefaultContextFormatter.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/memory/JdbcConversationMemorySummaryService.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/memory/JdbcConversationMemorySummaryService.java)

### 这一阶段要看懂什么

- 检索结果如何被拼装成 Prompt
- 历史消息如何参与当前问答
- 为什么历史对话太多时要做摘要压缩

### 读到这里时，你应该能回答

- 模型最终收到的内容包含哪些部分
- 为什么不是把所有历史消息都直接发给模型

## 阶段六：看模型调用和模型路由

这一块在 `infra-ai` 模块中，属于 AI 基础设施层。

### 推荐阅读方向

- [infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/embedding/RoutingEmbeddingService.java](/Users/mqf/project/ragent/infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/embedding/RoutingEmbeddingService.java)
- `infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/chat/`
- `infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/embedding/`
- `infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/rerank/`
- [bootstrap/src/main/resources/application.yaml](/Users/mqf/project/ragent/bootstrap/src/main/resources/application.yaml)

### 这一阶段要看懂什么

- 为什么项目同时接了多个模型供应商
- 不同模型能力如何被统一封装
- 模型失败时为什么可以切换或降级

### 这一阶段先抓设计，不抓细节

你不用先搞清楚每个 HTTP 参数。你只需要先理解：

- `chat` 是对话模型
- `embedding` 是把文本变成向量
- `rerank` 是对检索结果重新排序

## 阶段七：看知识库入库流水线

这部分是项目工程化程度很高的一块，建议放在聊天主链路之后阅读。

### 推荐阅读顺序

- [bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/controller/IngestionTaskController.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/controller/IngestionTaskController.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/service/impl/IngestionTaskServiceImpl.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/service/impl/IngestionTaskServiceImpl.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/engine/IngestionEngine.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/engine/IngestionEngine.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/node/IngestionNode.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/node/IngestionNode.java)
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/node/` 下各个具体节点

### 这一阶段要看懂什么

- 上传文档之后，系统为什么不能马上检索
- 文档要经过哪些处理步骤
- 为什么这里会用 `Pipeline`
- 节点编排如何让流程更容易扩展

### 你可以先把这条链路理解成

上传文件 -> 解析文本 -> 切分内容 -> 生成向量 -> 写入向量存储

## 阶段八：看缓存、限流、消息队列、定时任务

这一层属于“企业项目增强项”。它们不是最先要看的，但会显著影响你对项目完整度的理解。

### 推荐阅读顺序

- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/intent/IntentTreeCacheManager.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/intent/IntentTreeCacheManager.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/aop/ChatQueueLimiter.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/aop/ChatQueueLimiter.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/knowledge/mq/KnowledgeDocumentChunkConsumer.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/knowledge/mq/KnowledgeDocumentChunkConsumer.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/mq/MessageFeedbackConsumer.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/mq/MessageFeedbackConsumer.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/knowledge/schedule/KnowledgeDocumentScheduleJob.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/knowledge/schedule/KnowledgeDocumentScheduleJob.java)

### 这一阶段要看懂什么

- 为什么意图树适合放缓存
- 为什么聊天要限流和排队
- 为什么文档处理适合异步化
- 为什么知识库文档需要定时刷新

### 这一步里你要建立的中间件认知

- `Redis`：缓存、锁、辅助状态
- `Redisson`：更高级的分布式控制能力
- `RocketMQ`：异步解耦
- `@Scheduled`：定时任务

## 阶段九：看 MCP

MCP 是这个项目比较新的能力，也是它和传统 RAG 项目拉开差异的地方。

### 推荐阅读顺序

- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/mcp/MCPToolRegistry.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/mcp/MCPToolRegistry.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/mcp/client/HttpMCPClient.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/mcp/client/HttpMCPClient.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/mcp/client/RemoteMCPToolExecutor.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/mcp/client/RemoteMCPToolExecutor.java)
- [mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/endpoint/MCPDispatcher.java](/Users/mqf/project/ragent/mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/endpoint/MCPDispatcher.java)
- [mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/executor/WeatherMCPExecutor.java](/Users/mqf/project/ragent/mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/executor/WeatherMCPExecutor.java)

### 这一阶段要看懂什么

- 模型什么时候会走知识检索，什么时候会走工具调用
- 工具能力是怎么注册和发现的
- 主系统是怎么请求 MCP Server 的

### 先建立一个直觉

可以先把 MCP 理解成“给大模型接工具箱”。模型不只是在回答问题，还可能根据意图去调用一个外部工具拿数据。

## 按天拆分的阅读建议

如果你想更稳一点，可以按 14 天推进：

1. 第 1 天：`README.md`、`pom.xml`
2. 第 2 天：`application.yaml`、启动类、模块结构
3. 第 3 天：`AuthController`、`AuthServiceImpl`
4. 第 4 天：`UserController`、`SaTokenConfig`、`UserContextInterceptor`
5. 第 5 天：`KnowledgeBaseController`、`KnowledgeBaseServiceImpl`
6. 第 6 天：知识库相关表结构和 DAO
7. 第 7 天：`RAGChatController`
8. 第 8 天：`RAGChatServiceImpl`、流式输出相关类
9. 第 9 天：`RetrievalEngine`
10. 第 10 天：`PgRetrieverService`、`MilvusRetrieverService`
11. 第 11 天：Prompt 和记忆相关类
12. 第 12 天：`infra-ai` 模块的模型调用与路由
13. 第 13 天：入库流水线 `ingestion`
14. 第 14 天：缓存、MQ、定时任务、MCP

## 阅读时最容易卡住的点

### 1. 看到包太多，不知道从哪进

永远先从 `controller` 进，再进 `service`，再追 `dao` 或 `core`。

### 2. 看到中间件就慌

先不要背 API，先问自己：它解决了什么问题。

例如：

- Redis 解决缓存和并发控制
- RocketMQ 解决异步解耦
- pgvector 和 Milvus 解决向量检索

### 3. 看到 AI 相关代码就觉得抽象

先把 AI 相关代码当作普通“远程服务调用”来看，重点先理解输入输出和调用链。

### 4. README 和代码不一致

优先相信：

1. `application.yaml`
2. 实际实现类
3. 数据库脚本

不要优先相信文档描述。

## 当前阶段必须先理解的中间件

如果你想用最短时间获得最大收益，建议优先补这些：

1. `PostgreSQL`
2. `Redis`
3. `SSE`
4. `RocketMQ`
5. `pgvector`

下面这些先知道用途即可，后面再细看：

- `Milvus`
- `RustFS` 或 S3 兼容对象存储
- `MCP`
- `Ollama`
- 外部模型平台

## 阅读完成后的目标

如果这份地图走完，你至少应该能讲清楚下面这些问题：

- 一个聊天请求如何从 Controller 走到模型输出
- RAG 为什么要先检索再生成
- 知识库文档为什么要走入库流水线
- Redis、RocketMQ、pgvector 在项目里的角色分别是什么
- MCP 和传统知识检索的区别是什么

到这一步，你就不再是“只会看 Java 语法”，而是已经开始具备阅读企业级 AI 应用项目的能力了。
