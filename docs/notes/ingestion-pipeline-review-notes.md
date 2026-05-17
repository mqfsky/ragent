# 简历点 1：文档入库 Pipeline 复习笔记

修改日期：2026-05-17

> 复习目标：围绕简历中的“节点编排式文档入库 Pipeline、Tika 多格式解析、固定大小与结构感知 Chunk 分块、Overlap 保留上下文”这条，能说清楚它是什么、处在 RAG 哪个阶段、代码怎么实现、两种处理模式有什么区别。

## 1. Pipeline 是什么

这里的 Pipeline 指的是“文档入库流水线”，不是用户提问时的大模型回答链路。

它的作用是把一份原始文档处理成可检索的知识库 Chunk：

```text
文档上传/拉取
  -> 获取原始字节
  -> 解析文本
  -> 可选增强
  -> 分块
  -> 生成 embedding
  -> 写入向量库
```

在整个 RAG 问答系统里，它属于“离线/准实时入库阶段”。用户真正提问时，系统会从向量库里检索这些已经入库的 Chunk，再组装 Prompt 调 LLM 生成答案。

面试回答可以说：

> Pipeline 的价值是把文档入库拆成可配置节点，而不是把解析、分块、索引写死在一个方法里。这样不同来源、不同格式、不同分块策略可以复用同一个执行引擎。

## 2. 入口：runChunkTask 的两种模式

核心代码在：

- `bootstrap/src/main/java/com/nageoffer/ai/ragent/knowledge/service/impl/KnowledgeDocumentServiceImpl.java`
- 方法：`runChunkTask(KnowledgeDocumentDO documentDO)`

`documentDO.processMode` 决定处理模式：

```text
chunk    -> 直接分块模式
pipeline -> 节点编排模式
```

两种模式中间流程不同，但最终都会收敛成：

```java
List<VectorChunk> chunkResults;
```

`VectorChunk` 里包含：

```java
private String chunkId;              // Chunk ID
private Integer index;               // 当前 chunk 在文档中的序号
private String content;              // Chunk 文本
private Map<String, Object> metadata;// 可选元数据
private float[] embedding;           // 向量
```

也就是说，不管是 chunk 模式还是 pipeline 模式，最终都必须产出“已经分块并带 embedding 的 VectorChunk 列表”，后面才能统一写业务库和向量库。

## 3. CHUNK 模式做了什么

CHUNK 模式走：

```java
runChunkProcess(documentDO)
```

核心流程：

```text
读取文件
  -> Tika 提取文本
  -> 根据 chunkStrategy 选择分块策略
  -> 执行分块
  -> 批量生成 embedding
  -> 返回 ChunkProcessResult
```

代码逻辑可以记成：

```java
String text = parserSelector
        .select(ParserType.TIKA.getType())
        .extractText(is, documentDO.getDocName());

ChunkingStrategy chunkingStrategy =
        chunkingStrategyFactory.requireStrategy(chunkingMode);

List<VectorChunk> chunks = chunkingStrategy.chunk(text, config);

chunkEmbeddingService.embed(chunks, embeddingModel);

return new ChunkProcessResult(chunks, extractDuration, chunkDuration, embedDuration);
```

特点：

- 直接使用 `documentDO.chunkStrategy` 和 `documentDO.chunkConfig`。
- 主路径是 `Extract -> Chunk -> Embed`。
- 不经过可编排的 parser/enhancer/chunker/indexer 节点链。

## 4. PIPELINE 模式做了什么

PIPELINE 模式走：

```java
runPipelineProcess(documentDO)
```

核心流程：

```text
读取 pipelineId
  -> 查询 PipelineDefinition
  -> 读取文件字节 rawBytes
  -> 构造 IngestionContext
  -> ingestionEngine.execute(...)
  -> 从 result.getChunks() 拿到 List<VectorChunk>
```

关键点：

```java
IngestionContext context = IngestionContext.builder()
        .taskId(docId)
        .pipelineId(pipelineId)
        .rawBytes(fileBytes)
        .mimeType(documentDO.getFileType())
        .vectorSpaceId(...)
        .skipIndexerWrite(true)
        .build();

IngestionContext result = ingestionEngine.execute(pipelineDef, context);
List<VectorChunk> chunks = result.getChunks();
```

`skipIndexerWrite(true)` 很重要：

> Pipeline 内可能配置了 indexer 节点，但在 `runChunkTask` 这条知识库文档分块链路里，indexer 只做校验和准备，不真正写向量库。最终写业务库和向量库由 `runChunkTask` 后面的统一持久化方法完成，避免重复写。

## 5. 两种模式最终怎么落库

两种模式最终都走：

```java
persistChunksAndVectorsAtomically(collectionName, docId, chunkResults)
```

这个方法做四件事：

```text
1. 删除旧的业务库 chunk
2. 保存新的业务库 chunk 到 t_knowledge_chunk
3. 删除旧的向量库数据
4. 写入新的向量库数据
5. 更新文档 chunk_count 和 status
```

代码骨架：

```java
knowledgeChunkService.deleteByDocId(docId);
knowledgeChunkService.batchCreate(docId, chunks);

vectorStoreService.deleteDocumentVectors(collectionName, docId);
vectorStoreService.indexDocumentChunks(collectionName, docId, chunkResults);

documentMapper.updateById(updateDocumentDO);
```

注意：

- 业务库 `t_knowledge_chunk` 保存的是可编辑文本信息，比如 `chunkId/index/content/contentHash/charCount/tokenCount`。
- 向量库保存的是向量检索需要的数据，比如 `chunkId/content/embedding/metadata`。

## 6. Pipeline 的节点定义

Pipeline 的节点是用户配置出来的，不是固定写死的。

节点配置类是：

```java
public class NodeConfig {
    private String nodeId;      // 节点唯一 ID，例如 parser-1
    private String nodeType;    // 节点类型，例如 parser/chunker/indexer
    private JsonNode settings;  // 节点参数
    private JsonNode condition; // 条件执行配置
    private String nextNodeId;  // 下一个节点 ID
}
```

一个 node 表示入库流程中的一个处理步骤，不一定都直接操作文本：

```text
fetcher  -> 获取原始字节 rawBytes
parser   -> 把 rawBytes 解析为 rawText
enhancer -> 对整篇文档做 AI 增强
chunker  -> 把文本分块，并生成 embedding
enricher -> 对每个 chunk 补充 metadata
indexer  -> 写入向量库或做写入前校验
```

节点之间通过 `nextNodeId` 串起来。

典型配置：

```text
fetcher-1 -> parser-1 -> enhancer-1 -> chunker-1 -> enricher-1 -> indexer-1
```

但执行顺序不是由 enum 固定决定的，而是由用户配置的 `nextNodeId` 决定。

## 7. Pipeline 执行引擎怎么做

执行入口：

```java
IngestionEngine#execute(PipelineDefinition pipeline, IngestionContext context)
```

执行流程：

```text
初始化 context.logs 和 RUNNING 状态
  -> 把 List<NodeConfig> 转成 nodeId -> NodeConfig Map
  -> validatePipeline 校验 nextNodeId 和环
  -> findStartNode 找起始节点
  -> executeChain 沿 nextNodeId 链式执行
  -> 每个节点执行前判断 condition
  -> 调用具体 IngestionNode
  -> 记录 NodeLog
  -> 返回处理后的 IngestionContext
```

执行引擎本身不关心“解析怎么解析、分块怎么分块”，它只负责调度。

真正的业务逻辑在各个 `IngestionNode` 实现类里。

## 8. 为什么 nextNodeId 会成环

因为 `nextNodeId` 是用户配置的连线，配置错了就会出现环。

正常：

```text
parser-1 -> chunker-1 -> indexer-1
```

错误：

```text
parser-1 -> chunker-1 -> parser-1
```

如果不检测，执行引擎会无限循环。

`validatePipeline` 的思路是：

```text
从每个节点沿 nextNodeId 往后走
用 path 记录当前路径
如果当前节点已经在 path 里，说明有环
```

面试回答：

> 因为 Pipeline 的连线是配置化的，用户可能把 A 指向 B，又把 B 指回 A。执行前用当前路径集合做环检测，避免运行时无限循环。

## 9. 每个节点具体做什么

### fetcher

作用：获取文件原始字节。

输入：

```text
DocumentSource
```

输出到上下文：

```java
context.setRawBytes(result.content());
context.setMimeType(result.mimeType());
```

为什么要做：

> 不同文档来源获取方式不同，比如本地、HTTP、S3、飞书。fetcher 把来源差异屏蔽掉，后面的 parser 只关心 rawBytes。

### parser

作用：把 PDF/Word/Excel/PPT 等二进制文档解析成文本。

主路径使用 Tika：

```java
DocumentParser parser = parserSelector.select(ParserType.TIKA.getType());
ParseResult result = parser.parse(context.getRawBytes(), mimeType, options);
context.setRawText(result.text());
```

为什么要做：

> RAG 检索依赖文本，不依赖原始二进制文件。parser 负责把多格式文档统一转成 rawText。

### enhancer

作用：文档级增强，发生在分块前。

它调用 LLM 后可能会：

```text
1. 生成 enhancedText
2. 提取文档级 keywords
3. 生成文档级 questions
4. 提取文档级 metadata
```

代码结果：

```java
case CONTEXT_ENHANCE -> context.setEnhancedText(response);
case KEYWORDS -> context.setKeywords(...);
case QUESTIONS -> context.setQuestions(...);
case METADATA -> context.getMetadata().putAll(...);
```

例子：

原文：

```text
报销需在发票开具后30天内提交。
```

增强后可能变成：

```text
本条规定适用于员工费用报销场景。员工取得发票后，应在发票开具日起30天内提交报销申请，逾期可能影响财务审核和报销到账。
```

关键区别：

> enhancer 可能改变后续参与分块的正文，因为 chunker 会优先使用 `enhancedText`。

### chunker

作用：把文本切成 `VectorChunk`，并生成 embedding。

代码逻辑：

```java
String text = hasText(context.getEnhancedText())
        ? context.getEnhancedText()
        : context.getRawText();

List<VectorChunk> chunks = chunker.chunk(text, chunkConfig);
chunkEmbeddingService.embed(chunks, null);
context.setChunks(chunks);
```

为什么要做：

> 向量检索的最小单位通常不是整篇文档，而是 chunk。chunk 太大会有噪声，chunk 太小会断上下文，所以需要分块策略和 overlap。

### enricher

作用：chunk 级增强，发生在分块后。

它通常不改 `chunk.content`，只改 `chunk.metadata`。

代码结果：

```java
case KEYWORDS -> chunk.getMetadata().put("keywords", ...);
case SUMMARY -> chunk.getMetadata().put("summary", ...);
case METADATA -> chunk.getMetadata().putAll(...);
```

例子：

chunk 内容：

```text
员工因公出差产生的交通费、住宿费，应在返程后7个工作日内提交报销申请。
```

enricher 可能补充：

```json
{
  "keywords": ["出差", "交通费", "住宿费", "报销", "7个工作日"],
  "summary": "本段说明员工出差费用报销的范围和提交时限。",
  "category": "财务制度"
}
```

关键区别：

```text
enhancer = 分块前，文档级增强，可能影响正文
enricher = 分块后，chunk 级增强，通常只补 metadata
```

### indexer

作用：把 chunk 写入向量库，或在 `skipIndexerWrite=true` 时只做校验和准备。

核心动作：

```java
List<VectorChunk> chunks = context.getChunks();
String collectionName = resolveCollectionName(context);

float[][] vectorArray = toArrayFromChunks(chunks, expectedDim);
ensureVectorSpace(collectionName);
List<JsonObject> rows = buildRows(...);

if (context.isSkipIndexerWrite()) {
    return NodeResult.ok("向量写入由调用方统一完成");
}

insertRows(collectionName, context.getTaskId(), rows);
```

注意：

> `IndexerNode` 本身主要负责向量库写入，不负责保存业务库 chunk。`runChunkTask` 场景中，保存业务库 chunk 的动作在 `persistChunksAndVectorsAtomically` 中完成。

## 10. collection 是什么

collection 是向量库里的集合，可以类比数据库表。

```text
PostgreSQL 里有 table
Milvus 里有 collection
```

一个 collection 中存放很多 chunk 向量：

```text
collection: kb_hr_policy

row:
  id = chunk-001
  docId = doc-001
  content = "员工请假需要提前..."
  embedding = [0.12, -0.03, ...]
  metadata = {...}
```

项目里一个知识库通常对应一个 collection：

```java
KnowledgeBaseDO kbDO = knowledgeBaseMapper.selectById(kbId);
String collectionName = kbDO.getCollectionName();
```

为什么需要 collection：

```text
1. 隔离不同知识库的数据
2. 控制检索范围
3. 向量库写入、删除、搜索都需要目标 collection
```

## 11. 固定大小分块和结构感知分块

### 固定大小分块

适合普通长文本。

核心思想：

```text
按 chunkSize 切分
用 overlapSize 保留相邻 chunk 的重叠上下文
边界尽量回退到换行或句末标点
```

好处：

```text
简单稳定
参数容易理解
适合普通文本
```

风险：

```text
可能切断标题、表格、代码块或段落结构
```

### 结构感知分块

适合 Markdown、规章制度、技术文档等结构明显的文本。

核心思想：

```text
先识别 Heading / Paragraph / CodeFence / Atomic
再在块边界打包成 chunk
尽量不切碎标题、段落、代码块
```

好处：

```text
chunk 语义更完整
更适合结构化文档
```

风险：

```text
chunk 大小不如固定大小严格
依赖文本结构识别质量
```

## 12. Overlap 为什么有用

Overlap 是相邻 chunk 之间保留一段重叠文本。

作用：

```text
缓解语义在边界处被切断的问题
让下一个 chunk 仍然带有上一段的上下文
```

例子：

```text
chunk1: ... 报销需要在发票开具后30天内提交
chunk2: 后30天内提交，逾期需要部门负责人审批 ...
```

如果没有 overlap，第二段可能不知道“30天内提交”指的是报销。

参数风险：

```text
overlap 太小：上下文断裂
overlap 太大：chunk 重复多，存储和检索噪声增加
```

## 13. 面试高频回答模板

### Pipeline 为什么要节点化

> 文档入库不是固定流程，不同来源、格式、清洗规则和分块策略都可能不同。节点化后，获取、解析、增强、分块、索引可以独立扩展，并通过执行日志定位失败节点。

### CHUNK 模式和 PIPELINE 模式区别

> CHUNK 模式是直接 `Tika -> 分块策略 -> embedding`，流程简单固定。PIPELINE 模式是通过 `IngestionEngine` 调度用户配置的节点链，可以插入 enhancer、enricher 等处理步骤。两种模式最终都会产出带 embedding 的 `List<VectorChunk>`，再统一写业务库和向量库。

### enhancer 和 enricher 区别

> enhancer 是文档级增强，发生在分块前，可能生成 `enhancedText` 并影响后续 chunk 内容。enricher 是 chunk 级增强，发生在分块后，通常不改 `chunk.content`，只给 `chunk.metadata` 补关键词、摘要或结构化字段。

### indexer 会保存 chunk 吗

> `IndexerNode` 主要负责向量库写入，不负责保存业务库 chunk。在 `runChunkTask` 场景中，由于设置了 `skipIndexerWrite=true`，Pipeline 内 indexer 不真正写向量库，最终统一由 `persistChunksAndVectorsAtomically` 保存 `t_knowledge_chunk` 并写入 Milvus。

### collection 是什么

> collection 是 Milvus 里的向量集合，类似关系数据库的表。一个知识库通常对应一个 collection，里面存放该知识库所有 chunk 的 embedding、content 和 metadata，检索时在目标 collection 内做向量相似度搜索。

## 14. 注意边界

不要夸大成完整 DAG 工作流引擎。

当前实现更像“单链式 Pipeline”：

```text
一个节点通过 nextNodeId 指向下一个节点
condition 可以控制当前节点是否跳过
不是复杂的多分支 DAG 路由
```

也不要说“Pipeline 一定提升召回率”。更稳的说法是：

> Pipeline 提升了入库流程的灵活性；更合理的解析、增强、分块和 overlap 策略，有助于提升 Chunk 质量，从而为后续检索效果打基础。
