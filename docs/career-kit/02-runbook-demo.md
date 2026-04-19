# 启动清单与演示脚本

这份文档的目标不是把所有组件研究透，而是帮你尽快达到“知道怎么跑、知道演示什么、知道看哪里”的状态。

## 系统依赖说明

从配置和代码可以看到，项目依赖这些核心组件：

- `PostgreSQL`
  主业务数据，如用户、会话、知识库、文档、Trace 等。
- `Redis`
  登录态、限流、分布式协调等。
- `RocketMQ`
  异步任务和消息处理。
- `Milvus` 或 `PG Vector`
  作为向量检索存储。
- `S3` 兼容对象存储
  用于文档文件存储。
- 模型服务
  可接 `百炼`、`SiliconFlow`、`Ollama` 等。

## 关键配置入口

- 后端应用配置：`bootstrap/src/main/resources/application.yaml`
- 前端依赖：`frontend/package.json`
- 前端代理与联调说明：`frontend/TESTING.md`

重点先记这些配置：

- 后端端口：`9090`
- 后端上下文路径：`/api/ragent`
- 默认 MCP 服务地址：`http://localhost:9099`
- 聊天接口：`/api/ragent/rag/v3/chat`

## 本地启动清单

### 后端

1. 准备数据库、Redis、消息队列、向量库、对象存储。
2. 检查 `application.yaml` 中的连接配置。
3. 启动主应用：

```bash
./mvnw -pl bootstrap spring-boot:run
```

4. 如需工具服务，再启动 MCP Server：

```bash
./mvnw -pl mcp-server spring-boot:run
```

### 前端

```bash
cd frontend
npm install
npm run dev
```

## 页面清单

从 `frontend/src/router.tsx` 可以确认这些页面是本次演示重点：

- `/chat`
- `/admin/dashboard`
- `/admin/knowledge`
- `/admin/intent-tree`
- `/admin/ingestion`
- `/admin/traces`
- `/admin/settings`
- `/admin/users`

## 最推荐的演示路径

如果时间紧，优先演示下面两条。

### 演示 A：后台管理链路

1. 登录系统。
2. 进入后台仪表板，说明这是管理入口。
3. 进入知识库管理页，展示知识库列表。
4. 进入文档管理页，说明可以上传文档、查看状态、触发分块。
5. 进入意图树页面，说明项目不是无脑检索，而是有意图识别。
6. 进入 Ingestion 页面，说明知识入库不是一条 SQL，而是流水线过程。
7. 进入 Trace 页面，说明系统可观测，不是黑盒调用模型。

### 演示 B：问答主链路

1. 在聊天页提问。
2. 说明前端通过 `/rag/v3/chat` 发起 SSE 流式请求。
3. 解释后端会做问题重写、意图识别、多通道检索、Rerank、模型回答。
4. 如果后台有 Trace 数据，再切到 `/admin/traces` 展示链路节点。

## 一份能直接照念的演示脚本

> 我这次主要想演示 Ragent 不是一个简单的聊天页面，而是一套完整的企业级 RAG 系统。  
>  
> 先看后台。这里有知识库管理、文档管理、意图树、入库监控和链路追踪。说明这个系统同时覆盖了数据侧、问答侧和运维侧。  
>  
> 在知识库这一侧，管理员可以创建知识库、上传文档、触发分块和向量化，文档真正变成可检索的知识。  
>  
> 在聊天这一侧，用户提问后不是直接丢给模型，而是先做问题理解，再进入检索和排序，最后再交给模型回答。  
>  
> 最后看 Trace 页面。这个页面能说明系统把检索、生成等关键节点都记录了下来，出了问题可以定位，而不是只能看日志猜。

## 最适合你的轻量实操背书

默认选择“跟一条真实调用链”，因为对当前阶段性价比最高。

### 调用链 1：聊天主链路

- 控制器：`RAGChatController#chat`
- 服务接口：`RAGChatService#streamChat`
- 实现类：`RAGChatServiceImpl`
- 价值：
  你可以讲 SSE 流式接口、Service 分层、模型流式输出、任务取消。

### 调用链 2：文档入库链路

- 控制器：`KnowledgeDocumentController#upload`
- 分块接口：`KnowledgeDocumentController#startChunk`
- 服务实现：`KnowledgeDocumentServiceImpl`
- 价值：
  你可以讲文件上传、入库记录、文本抽取、Chunk 分块、Embedding 和向量写库。

推荐优先选调用链 2，因为更能体现 RAG 的工程味道。

## 你在面试里可以说“我做过验证”的内容

- 我核对过前后端实际入口和页面路由。
- 我确认过聊天接口是 SSE 流式输出。
- 我确认过文档上传和分块是两个明确接口，不是一个黑盒动作。
- 我确认过后台具备知识库、意图树、Ingestion、Trace 等页面，说明系统具备管理和观测能力。

## 第二阶段自测

- 能说出最少 5 个后台页面及用途
- 能说出聊天和知识库各 1 条真实接口
- 能独立描述一条演示脚本
- 能说明为什么这个项目“不是只调模型 API”

