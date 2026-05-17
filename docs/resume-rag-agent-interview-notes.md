# RAG 智能体知识问答平台简历点备考笔记

> 用途：这份文档记录简历上每个表述在 ragent 项目里的源码落点、为什么这样写、以及面试时可以展开的回答方向。后续准备面试时，可以直接围绕本文逐条追问。

## 最终简历表述

**RAG 智能体知识问答平台**，基于开源项目进行二次开发，围绕智能文档检索与问答场景，提供多通道检索引擎、意图识别、问题重写、会话记忆、MCP 工具调用等核心能力。

技术架构：Spring Boot + MyBatis Plus + PostgreSQL + Milvus + Redis + Redisson + Apache Tika + Sa-Token

通过多通道检索引擎实现意图定向检索与全局向量检索双路召回，基于 CompletableFuture 并行执行检索通道，并通过去重和 Rerank 优化结果相关性，解决单一向量检索容易漏召回的问题。

基于 Redisson 信号量 + Redis ZSET + Pub/Sub 实现分布式公平排队限流，使用 Lua 脚本保证队头抢占与过期清理的原子性，支持最大并发控制、等待超时拒绝和请求取消清理，缓解高并发下的大模型调用压力。

实现多模型路由与熔断降级机制，按候选模型优先级进行调用，结合 CLOSED / OPEN / HALF_OPEN 三态健康状态记录模型可用性；流式场景下通过首包探测切换至下一候选模型，降低单模型调用失败对问答链路的影响。

实现会话记忆管理机制，采用滑动窗口保留最近 N 轮对话，超过阈值后异步生成摘要并持久化至 PostgreSQL；加载上下文时并行读取历史消息与摘要，在保留关键上下文的同时控制长对话 Token 成本。

设计节点编排式文档入库 Pipeline，抽象文档获取、解析、增强、分块、索引等处理节点，支持节点串联、条件跳转、环检测和执行日志记录；基于 Apache Tika 实现多格式文档解析，并提供固定大小与结构感知两类 Chunk 分块策略，通过 Overlap 保留上下文，提升入库流程灵活性与 Chunk 质量。

## 总体主链路

核心入口是流式问答 Pipeline：

- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/pipeline/StreamChatPipeline.java`
- 执行顺序：加载记忆 -> 问题重写/拆分 -> 意图识别 -> 歧义引导 -> SYSTEM 短路 -> KB/MCP 检索 -> Prompt 组装 -> LLM 流式回答。

可以这样讲：

> 这个项目不是简单调用大模型接口，而是把一次问答拆成多个工程阶段：先补全历史上下文，再改写用户问题并拆分子问题，然后用意图树判断走知识库、系统回答还是 MCP 工具，最后把 KB 与 MCP 的结果统一组装成 Prompt 交给模型。

## 1. 多通道检索与双路召回

### 简历表述

通过多通道检索引擎实现意图定向检索与全局向量检索双路召回，基于 CompletableFuture 并行执行检索通道，并通过去重和 Rerank 优化结果相关性，解决单一向量检索容易漏召回的问题。

### 源码落点

- 多通道调度：`bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/MultiChannelRetrievalEngine.java`
- 总检索编排：`bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/RetrievalEngine.java`
- 意图定向通道：`bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/channel/IntentDirectedSearchChannel.java`
- 全局向量通道：`bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/channel/VectorGlobalSearchChannel.java`
- 通道内并行模板：`bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/channel/AbstractParallelRetriever.java`
- 去重处理器：`bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/postprocessor/DeduplicationPostProcessor.java`
- Rerank 处理器：`bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/postprocessor/RerankPostProcessor.java`
- Rerank 路由：`infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/rerank/RoutingRerankService.java`

### 实现逻辑

`MultiChannelRetrievalEngine#retrieveKnowledgeChannels` 先构建 `SearchContext`，再筛选启用的 `SearchChannel`，使用 `CompletableFuture.supplyAsync` 并行执行各检索通道。检索结果返回后，按 `SearchResultPostProcessor#getOrder` 执行后处理链。

当前最核心的两个检索通道是：

- `IntentDirectedSearchChannel`：从意图识别结果中过滤 KB 类型意图，根据意图节点定向检索对应知识库。
- `VectorGlobalSearchChannel`：在意图为空、置信度不足或需要补充时，从所有知识库 collection 做全局向量检索。

后处理链里：

- `DeduplicationPostProcessor` 基于 chunk id 或内容 hash 去重，多通道命中同一 chunk 时保留高分结果。
- `RerankPostProcessor` 调用 `RerankService` 对候选 chunk 精排，输出最终 TopK。

### 为什么这样写

单一路径的向量检索容易出现两个问题：

- 只做全局检索：召回范围大，但结果可能偏散，和业务意图不够贴合。
- 只做意图定向检索：精准但依赖意图识别，如果意图置信度不高，容易漏召回。

所以简历里写“双路召回”是为了突出召回覆盖率和精准度之间的平衡。

### 面试可展开

如果问“为什么不用单一 Milvus 检索”：

> 因为企业知识库通常按业务域、系统、文档类型划分。意图定向检索能缩小范围，提高精准度；全局向量检索作为低置信度或无意图时的补充，避免用户表达模糊时完全搜不到。最终通过去重和 Rerank 把多路结果合并。

如果问“CompletableFuture 用在哪里”：

> 两层并行：第一层是多检索通道并行；第二层是通道内部对多个意图或多个 collection 并行检索。这样可以减少串行访问多个知识库的耗时。

### 注意边界

不要说“显著提升准确率”，除非有评测数据。可以说“优化结果相关性”“提升召回覆盖率”。

## 2. 分布式公平排队限流

### 简历表述

基于 Redisson 信号量 + Redis ZSET + Pub/Sub 实现分布式公平排队限流，使用 Lua 脚本保证队头抢占与过期清理的原子性，支持最大并发控制、等待超时拒绝和请求取消清理，缓解高并发下的大模型调用压力。

### 源码落点

- 公平限流器：`bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/ratelimit/FairDistributedRateLimiter.java`
- 聊天入口限流包装：`bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/ratelimit/ChatQueueLimiter.java`
- 限流 Bean 配置：`bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/config/ChatRateLimiterConfig.java`
- 限流参数：`bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/config/RAGRateLimitProperties.java`
- Lua 脚本：`bootstrap/src/main/resources/lua/queue_claim_atomic.lua`
- SSE 入口：`bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/impl/RAGChatServiceImpl.java`

### 实现逻辑

`ChatQueueLimiter#enqueue` 是问答请求进入模型调用前的限流入口。如果全局限流开启，请求会进入 `FairDistributedRateLimiter#acquire`。

限流器内部使用：

- Redisson `RPermitExpirableSemaphore` 控制全局最大并发。
- Redis ZSET 保存等待队列，score 是递增序号，用于保持 FIFO 公平性。
- entry marker key 标记请求存活，并设置 TTL，避免节点宕机导致队列僵尸项一直占位。
- Pub/Sub 在 permit 释放或队列变化时通知其他实例唤醒本地 poller。
- Lua 脚本负责从队头窗口原子 claim 请求，同时清理过期 entry。

请求状态机是 `PENDING -> GRANTED / TIMED_OUT / CANCELLED`。拿到 permit 后执行业务回调，并在 finally 中释放 permit；超时或取消会清理 ZSET 和 entry marker。

### 为什么这样写

大模型调用和流式响应通常耗时长、成本高，而且外部模型服务有限流。只靠本机线程池无法解决多实例部署下的全局并发问题，所以用 Redis/Redisson 做分布式并发控制。

写“公平排队”是因为队列用 ZSET 顺序号维护 FIFO，并且只有队头窗口内的请求可以 claim permit。

写“Lua 原子性”是因为队头判断、僵尸清理、出队 claim 如果拆成多次 Redis 操作，会在多实例并发下出现重复抢占或顺序错乱。

### 面试可展开

如果问“为什么不用 Redisson RRateLimiter”：

> RRateLimiter 更偏固定速率控制，而这里需要控制长耗时模型任务的并发槽位，还要支持等待队列、公平排队、超时拒绝和取消清理，所以使用可过期信号量 + 自定义 ZSET 队列更合适。

如果问“Lua 保证了什么”：

> 保证队头判断、过期项清理和当前请求出队 claim 在 Redis 内一次完成，避免多个应用实例同时看到自己可执行。

### 注意边界

不要说“SSE 实时推送排队位置”，当前代码主要支持超时拒绝和完成事件，不是完整队列位置推送。

## 3. 多模型路由与熔断降级

### 简历表述

实现多模型路由与熔断降级机制，按候选模型优先级进行调用，结合 CLOSED / OPEN / HALF_OPEN 三态健康状态记录模型可用性；流式场景下通过首包探测切换至下一候选模型，降低单模型调用失败对问答链路的影响。

### 源码落点

- 模型路由执行器：`infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/model/ModelRoutingExecutor.java`
- 模型选择器：`infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/model/ModelSelector.java`
- 健康状态/熔断器：`infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/model/ModelHealthStore.java`
- LLM 路由服务：`infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/chat/RoutingLLMService.java`
- 流式首包探测桥：`infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/chat/ProbeStreamBridge.java`
- Provider 客户端：`infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/chat/BaiLianChatClient.java`、`OllamaChatClient.java`、`SiliconFlowChatClient.java`
- 模型配置：`infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/config/AIModelProperties.java`

### 实现逻辑

非流式调用走 `ModelRoutingExecutor#executeWithFallback`：

1. `ModelSelector` 选出候选模型列表。
2. 按候选顺序遍历。
3. 通过 `ModelHealthStore#allowCall` 判断当前模型是否可调用。
4. 调用成功则 `markSuccess`，失败则 `markFailure` 并切换下一候选。
5. 所有候选失败才抛出 `RemoteException`。

熔断状态在 `ModelHealthStore`：

- `CLOSED`：正常可调用。
- `OPEN`：连续失败达到阈值后打开，openUntil 前拒绝调用。
- `HALF_OPEN`：熔断时间到后允许一个探测请求，成功回到 CLOSED，失败重新 OPEN。

流式调用走 `RoutingLLMService#streamChat`，每个候选模型会先创建 `ProbeStreamBridge`。它会等待首包：

- 收到内容或 thinking：认为成功，提交缓冲内容给前端。
- 收到错误、超时、无内容完成：取消当前流，标记失败，切换下一候选模型。

### 为什么这样写

RAG 问答链路依赖外部模型服务，模型可能出现超时、限流、网络失败、首包慢等问题。如果直接绑定单模型，用户请求会被单点故障影响。

多模型候选 + 熔断可以把故障模型临时摘除；流式首包探测可以避免把失败模型的错误或半截输出直接暴露给用户。

### 面试可展开

如果问“为什么流式场景要首包探测”：

> 流式接口一旦把内容发给前端，就很难无感切换模型。首包探测先用桥接器缓存事件，确认当前模型能正常输出后再提交缓冲；如果首包超时或报错，就取消当前模型并切换候选。

如果问“三态熔断怎么恢复”：

> OPEN 到期后进入 HALF_OPEN，只允许一个探测请求。探测成功说明模型恢复，状态回 CLOSED；探测失败重新 OPEN。

### 注意边界

不要说“用户端完全无感”。更稳的说法是“候选模型可用时可以自动切换，降低单模型失败影响”。所有候选都失败仍然会报错。

## 4. 会话记忆与摘要压缩

### 简历表述

实现会话记忆管理机制，采用滑动窗口保留最近 N 轮对话，超过阈值后异步生成摘要并持久化至 PostgreSQL；加载上下文时并行读取历史消息与摘要，在保留关键上下文的同时控制长对话 Token 成本。

### 源码落点

- 记忆服务接口：`bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/memory/ConversationMemoryService.java`
- 默认记忆服务：`bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/memory/DefaultConversationMemoryService.java`
- JDBC 消息存储：`bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/memory/JdbcConversationMemoryStore.java`
- 摘要服务：`bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/memory/JdbcConversationMemorySummaryService.java`
- 记忆配置：`bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/config/MemoryProperties.java`
- 消息实体：`bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/dao/entity/ConversationMessageDO.java`
- 摘要实体：`bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/dao/entity/ConversationSummaryDO.java`
- 数据表：`resources/database/schema_pg.sql`

### 实现逻辑

在 `StreamChatPipeline#loadMemory` 中，每次问答开始会调用 `memoryService.loadAndAppend`，先把用户问题写入记忆，再加载历史上下文。

`DefaultConversationMemoryService#load` 使用两个 `CompletableFuture` 并行执行：

- 从摘要表读取最近一条摘要。
- 从消息表读取最近 N 轮历史对话。

最后把摘要包装为 system message，再拼接最近历史消息。

`JdbcConversationMemoryStore#loadHistory` 根据 `historyKeepTurns * 2` 控制最近消息数，只保留 user/assistant，并裁剪到 user 开头，避免上下文从 assistant 消息开始。

`JdbcConversationMemorySummaryService#compressIfNeeded` 在 assistant 消息写入后触发。如果开启摘要、消息角色是 assistant、用户消息数达到阈值，就异步执行摘要压缩，并用 Redisson lock 防止同一会话并发摘要。

摘要生成会：

- 读取上一次摘要。
- 找出应该被压缩的历史消息区间。
- 调用 LLM 合并旧摘要和本轮待压缩消息。
- 写入 PostgreSQL 的 conversation summary 表。

### 为什么这样写

长对话如果每次把完整历史都塞进 Prompt，会导致：

- Token 成本越来越高。
- 模型上下文窗口被历史挤满。
- 响应速度变慢。

滑动窗口保留最近上下文，摘要保留早期关键信息，是一个成本和上下文完整性的折中。

### 面试可展开

如果问“为什么摘要异步做”：

> 摘要不是当前响应的强依赖，如果同步执行会拉长用户本次问答耗时。项目在 assistant 消息落库后异步触发摘要，用锁避免并发重复摘要。

如果问“加载时为什么并行读摘要和历史”：

> 两者来自不同查询路径，没有强依赖，并行读取可以减少构造 Prompt 前的等待时间。

### 注意边界

不要说“完全避免 Token 爆炸”。更稳的说法是“控制长对话 Token 成本”。

## 5. 文档入库 Pipeline 与 Chunk 分块

### 简历表述

设计节点编排式文档入库 Pipeline，抽象文档获取、解析、增强、分块、索引等处理节点，支持节点串联、条件跳转、环检测和执行日志记录；基于 Apache Tika 实现多格式文档解析，并提供固定大小与结构感知两类 Chunk 分块策略，通过 Overlap 保留上下文，提升入库流程灵活性与 Chunk 质量。

### 源码落点

- Pipeline 服务：`bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/service/impl/IngestionPipelineServiceImpl.java`
- Pipeline 定义：`bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/domain/pipeline/PipelineDefinition.java`
- 节点配置：`bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/domain/pipeline/NodeConfig.java`
- 执行引擎：`bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/engine/IngestionEngine.java`
- 节点接口：`bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/node/IngestionNode.java`
- 节点实现：`FetcherNode.java`、`ParserNode.java`、`EnricherNode.java`、`EnhancerNode.java`、`ChunkerNode.java`、`IndexerNode.java`
- Tika 解析器：`bootstrap/src/main/java/com/nageoffer/ai/ragent/core/parser/TikaDocumentParser.java`
- Markdown 解析器：`bootstrap/src/main/java/com/nageoffer/ai/ragent/core/parser/MarkdownDocumentParser.java`
- 解析器选择：`bootstrap/src/main/java/com/nageoffer/ai/ragent/core/parser/DocumentParserSelector.java`
- 分块模式：`bootstrap/src/main/java/com/nageoffer/ai/ragent/core/chunk/ChunkingMode.java`
- 固定大小分块：`bootstrap/src/main/java/com/nageoffer/ai/ragent/core/chunk/strategy/FixedSizeTextChunker.java`
- 结构感知分块：`bootstrap/src/main/java/com/nageoffer/ai/ragent/core/chunk/strategy/StructureAwareTextChunker.java`
- 分块节点：`bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/node/ChunkerNode.java`
- 向量写入：`bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/node/IndexerNode.java`

### 实现逻辑

`IngestionPipelineServiceImpl` 负责 Pipeline 的创建、更新、查询和节点配置持久化。每个节点保存：

- `nodeId`
- `nodeType`
- `nextNodeId`
- `settingsJson`
- `conditionJson`

`IngestionEngine#execute` 执行 Pipeline：

1. 根据节点列表构建 `nodeId -> NodeConfig` 映射。
2. `validatePipeline` 校验 nextNodeId 是否存在，并检查是否有环。
3. `findStartNode` 找到没有被其他节点引用的起始节点。
4. `executeChain` 从起始节点按 `nextNodeId` 串行执行。
5. 每个节点执行前先判断 condition，执行后记录 `NodeLog`，包括节点 id、类型、消息、耗时、成功状态、错误和输出。

文档解析：

- `ParserNode` 根据 MIME 或文件名识别类型，当前主要选择 Tika 解析器。
- `TikaDocumentParser` 调用 Apache Tika 提取文本，并做文本清洗。
- Markdown 有单独解析器，保留原始 Markdown 文本。

Chunk 分块：

- `ChunkingMode.FIXED_SIZE`：按固定大小和 overlap 分块，适合普通文本。
- `ChunkingMode.STRUCTURE_AWARE`：面向 Markdown/结构化文本，尽量保留标题、段落等边界。
- `ChunkerNode` 根据配置选择策略，生成 `VectorChunk`，再调用 `ChunkEmbeddingService` 生成向量。

### 为什么这样写

RAG 效果不只取决于问答阶段，知识入库质量也很关键。Pipeline 的价值是把文档处理拆成可组合节点，便于不同来源、不同格式、不同分块策略复用。

Chunk 分块的价值是控制向量检索的最小语义单元：

- chunk 太大：召回内容噪声多，Prompt 成本高。
- chunk 太小：上下文断裂，答案缺信息。
- overlap：缓解相邻 chunk 之间语义断裂。
- 结构感知：尽量避免把标题、列表、段落切碎。

### 面试可展开

如果问“Pipeline 为什么要做成节点编排”：

> 文档入库不是固定流程，不同来源和不同文件类型可能需要不同处理步骤。节点化以后，获取、解析、增强、分块、索引可以独立扩展，也能记录每个节点的执行日志，方便排查入库失败位置。

如果问“固定大小和结构感知分块怎么选”：

> 普通长文本可以用固定大小 + overlap，简单稳定；Markdown 或有明显章节结构的文档适合结构感知分块，尽量保留标题和段落边界，提高 chunk 的语义完整性。

### 注意边界

不要说“显著提升召回率”，除非有评测数据。可以说“提升入库流程灵活性与 Chunk 质量”。

## 其他支撑点：意图识别、问题重写、MCP、Trace

这些点出现在开头项目介绍中，面试可能被追问，但不一定要在简历 bullet 中全部展开。

### 意图识别

源码：

- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/intent/DefaultIntentClassifier.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/intent/IntentResolver.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/intent/IntentTreeCacheManager.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/enums/IntentKind.java`

要点：

- 从 Redis 缓存加载意图树，缓存未命中从 PostgreSQL 加载。
- LLM 对叶子意图节点打分。
- 意图类型包括 KB、SYSTEM、MCP。
- `IntentResolver` 对每个子问题并行做意图分类。

可讲：

> 意图识别决定问题走知识库检索、系统回答还是 MCP 工具调用，是 RAG 流程的路由层。

### 问题重写与拆分

源码：

- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/rewrite/MultiQuestionRewriteService.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/rewrite/QueryTermMappingService.java`

要点：

- 先做术语归一化。
- 调 LLM 输出 rewrite 和 sub_questions。
- 失败时降级为归一化问题。
- 关闭 LLM 重写时走规则拆分。

可讲：

> 问题重写用来补全口语化表达和多轮上下文，拆分用于复杂问题并行检索多个子问题。

### MCP 工具调用

源码：

- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/RetrievalEngine.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/mcp/McpToolRegistry.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/mcp/DefaultMcpToolRegistry.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/mcp/LLMMcpParameterExtractor.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/mcp/McpClientAutoConfiguration.java`
- `mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/executor/*`

要点：

- 意图节点可以绑定 MCP tool id。
- `RetrievalEngine` 分离 KB 意图和 MCP 意图。
- 多个 MCP 工具可通过 `CompletableFuture` 并行调用。
- 参数由 LLM 根据工具 schema 和用户问题提取。

可讲：

> MCP 把 RAG 从静态知识库扩展到外部实时系统调用，例如天气、工单、销售数据等工具。

### Trace 与异步上下文

源码：

- `framework/src/main/java/com/nageoffer/ai/ragent/framework/trace/RagTraceContext.java`
- `framework/src/main/java/com/nageoffer/ai/ragent/framework/context/UserContext.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/aop/RagTraceAspect.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/config/ThreadPoolExecutorConfig.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/trace/StreamChatTraceRunner.java`

要点：

- `RagTraceContext` 和 `UserContext` 使用 `TransmittableThreadLocal`。
- 多个业务线程池通过 `TtlExecutors` 包装。
- Trace AOP 记录 trace run 和 node，包括 traceId、节点层级、状态、耗时、异常、类和方法。

注意：

- 简历里没有单独写这一条，是因为它更像支撑能力。
- 不要说“记录完整输入输出”，当前 AOP 主要记录层级、耗时、状态和异常，`extraData` 是预留字段。

## 技术栈为什么这么写

简历技术架构：

`Spring Boot + MyBatis Plus + PostgreSQL + Milvus + Redis + Redisson + Apache Tika + Sa-Token`

对应关系：

- Spring Boot：主应用、配置、Controller/Service/AOP。
- MyBatis Plus：数据库 CRUD、分页、实体映射。
- PostgreSQL：会话消息、摘要、Trace、知识库元数据、Pipeline 配置等关系型数据。
- Milvus：向量索引和向量检索能力。
- Redis：缓存、意图树缓存、术语映射缓存、队列 entry、Pub/Sub 等。
- Redisson：分布式信号量、锁、Topic、Bucket、ZSET 操作。
- Apache Tika：多格式文档文本抽取。
- Sa-Token：登录认证和用户上下文来源。

如果投全栈岗位，可以补充 `React/Vite`；如果投后端岗位，不写也可以。

## 简历表达边界

建议保留：

- “基于开源项目二次开发”：边界清楚，避免被认为从零实现所有模块。
- “降低影响”“缓解压力”“优化相关性”：稳健，不需要实验数据。
- “支持最大并发控制、等待超时拒绝和请求取消清理”：代码有支撑。

建议避免：

- “用户端完全无感”：所有候选模型都失败时仍会报错。
- “SSE 实时推送排队位置”：当前没有完整队列位置推送。
- “全链路追踪记录输入输出”：当前主要记录节点、层级、耗时、状态和异常。
- “显著提升召回率/准确率”：除非有评测或压测数据。
- “配置 8 个专用线程池”：实际线程池数量不止 8，且写死数字容易被追问。

## 快速复习提纲

面试前按这 5 个问题自测：

1. 双路召回解决了什么问题？意图定向和全局向量分别什么时候启用？
2. Redis 公平队列为什么需要 ZSET + Lua？Lua 原子操作覆盖哪些步骤？
3. 三态熔断如何流转？流式首包探测为什么需要先缓存再提交？
4. 会话记忆为什么要滑动窗口 + 摘要？摘要什么时候触发，为什么异步？
5. 文档入库 Pipeline 如何防环？固定大小分块和结构感知分块分别适合什么场景？

