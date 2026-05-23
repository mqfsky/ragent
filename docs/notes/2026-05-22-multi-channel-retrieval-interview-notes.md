# 多通道检索与意图定向检索面试笔记

日期：2026-05-22

> 本文整理围绕简历点“基于意图识别的多通道检索、定向检索、全局向量检索、并行召回、去重与 Rerank”的追问与回答，便于后续面试复习。

## 1. 在项目里，“意图”是什么

在这个项目中，意图不是泛泛的“用户想做什么”，而是系统预先配置在 `t_intent_node` 表中的意图树节点。

每个意图节点可以包含：

- `intentCode`：业务唯一标识。
- `parentCode`：父节点标识。
- `name`：展示名称。
- `description`：语义描述，用于意图识别。
- `examples`：典型问题示例。
- `kind`：意图类型，包括 `KB`、`SYSTEM`、`MCP`。
- `collectionName`：知识类意图绑定的向量检索范围。
- `mcpToolId`：工具类意图绑定的 MCP 工具。
- `topK`：节点级检索数量。
- `promptTemplate` / `promptSnippet`：命中该意图后的提示词配置。

面试表达：

> 在这个项目里，意图本质上是 RAG 链路的路由标签。它配置在意图树中，命中不同类型的意图后，系统会决定走知识库检索、MCP 工具调用，还是系统回答短路。

## 2. 意图树是如何配置的，结构是什么

意图树在业务上是树形结构，但数据库中使用扁平的邻接表方式存储。

核心字段：

```text
intent_code   当前节点唯一标识
parent_code   父节点 intent_code
level         层级，0=DOMAIN，1=CATEGORY，2=TOPIC
```

示例结构：

```text
业务系统 DOMAIN
└── OA系统 CATEGORY
    ├── 系统介绍 TOPIC
    └── 数据安全 TOPIC

集团信息化 DOMAIN
└── 人事 CATEGORY
    └── 人事制度 TOPIC

系统交互 DOMAIN
└── 介绍自己 TOPIC

动态数据 DOMAIN
└── 销售查询 TOPIC
```

面试表达：

> 意图树业务上是树形的，但数据库里用 `intent_code + parent_code` 的邻接表结构存储。这样方便增删改查，运行时再组装成真正的树。

## 3. 代码里如何把扁平节点变成树

项目里有两套建树逻辑。

后台展示使用 `IntentTreeServiceImpl#getFullTree`：

1. 查询所有未删除节点。
2. 按 `parentCode` 分组。
3. `parentCode == null` 的节点作为根节点。
4. 从根节点开始递归调用 `buildTree`。
5. 每个节点用自己的 `intentCode` 去 `parentMap` 查子节点，然后设置到 `children`。

关键代码思路：

```java
Map<String, List<IntentNodeDO>> parentMap = list.stream()
        .collect(Collectors.groupingBy(node -> {
            String parent = node.getParentCode();
            return parent == null ? "ROOT" : parent;
        }));

List<IntentNodeDO> roots = parentMap.getOrDefault("ROOT", Collections.emptyList());

private IntentNodeTreeVO buildTree(IntentNodeDO current,
                                   Map<String, List<IntentNodeDO>> parentMap) {
    IntentNodeTreeVO result = BeanUtil.toBean(current, IntentNodeTreeVO.class);
    List<IntentNodeDO> children = parentMap.getOrDefault(current.getIntentCode(), Collections.emptyList());

    if (!CollectionUtils.isEmpty(children)) {
        List<IntentNodeTreeVO> childVOs = children.stream()
                .map(child -> buildTree(child, parentMap))
                .collect(Collectors.toList());
        result.setChildren(childVOs);
    }
    return result;
}
```

运行时识别使用 `DefaultIntentClassifier#loadIntentTreeFromDB`：

1. 查询 `deleted=0` 且 `enabled=1` 的节点。
2. 第一遍把所有 `IntentNodeDO` 转成 `IntentNode`，放入 `id2Node`。
3. 第二遍根据 `parentId` 找父节点。
4. 找到父节点则 `parent.children.add(node)`。
5. 找不到父节点或没有父节点则作为根节点。

面试表达：

> 后台展示用按父节点分组加递归的方式建树；运行时识别用两遍建树，第一遍构建 `id -> node` 映射，第二遍通过 `parentId` 找父节点，把当前节点挂到父节点的 `children` 中。

## 4. 改写拆分后如何打分

问题改写拆分后，系统会进入 `IntentResolver#resolve`。

如果 `RewriteResult` 中有多个子问题，就对每个子问题分别识别意图；如果没有拆分，就使用改写后的问题作为唯一子问题。

关键流程：

```text
RewriteResult
  -> subQuestions
  -> 每个子问题 CompletableFuture 并行分类
  -> 加载意图树叶子节点
  -> 拼 Prompt：id/path/description/type/examples
  -> LLM 输出 id + score
  -> 解析成 NodeScore
  -> score 降序排序
  -> 过滤 score < 0.35
  -> 最多保留 3 个意图
  -> SubQuestionIntent
```

关键点：

- `IntentResolver` 会用 `CompletableFuture` 并行处理多个子问题。
- `DefaultIntentClassifier` 会加载意图树，并只取叶子节点参与分类。
- 它把叶子节点的 `id`、`path`、`description`、`type`、`examples` 拼进 Prompt。
- LLM 返回 JSON 数组，例如：

```json
[
  {"id": "biz-oa-security", "score": 0.88, "reason": "问题涉及 OA 系统数据安全要求"}
]
```

- 代码再把 `id` 映射回 `IntentNode`，封装成 `NodeScore`。

按分数排序的位置在 `DefaultIntentClassifier#classifyTargets`：

```java
scores.sort(Comparator.comparingDouble(NodeScore::getScore).reversed());
```

面试表达：

> 改写拆分后，系统对每个子问题并行做意图识别。分类器只把意图树叶子节点作为候选，交给 LLM 输出 `id + score`，再由代码解析成 `NodeScore`，按分数降序排序，过滤低分候选。

## 5. 并行意图分类是否意味着并行调大模型

是的。如果问题被拆成多个子问题，那么每个子问题会通过 `CompletableFuture.supplyAsync` 并行执行 `classifyIntents`。

而 `classifyIntents` 最终会调用：

```java
llmService.chat(request);
```

因此，多子问题场景下，意图分类阶段就是并行调用大模型。

边界：

- 如果只有一个子问题，只会调用一次大模型。
- 如果有多个子问题，会并行调用多次大模型。

## 6. `capTotalIntents` 是什么操作

`capTotalIntents` 是对整次请求的意图数量做全局裁剪。

前面的 `classifyIntents` 已经对单个子问题做了限制：

```java
return scores.stream()
        .filter(ns -> ns.getScore() >= INTENT_MIN_SCORE)
        .limit(MAX_INTENT_COUNT)
        .toList();
```

但如果一个问题拆成多个子问题，每个子问题都保留 3 个意图，总意图数量可能会很多。后续每个意图都可能触发检索或工具调用，导致 fan-out 过大。

`capTotalIntents` 的策略：

1. 统计所有子问题的候选意图总数。
2. 如果总数不超过 `MAX_INTENT_COUNT`，直接返回。
3. 如果超限，先保证每个子问题至少保留 1 个最高分意图。
4. 剩余名额再按全局分数从高到低补齐。
5. 最后重新组装成 `SubQuestionIntent`。

示例：

```text
子问题1：A 0.92，B 0.80
子问题2：C 0.88，D 0.70
子问题3：E 0.60
```

如果总上限是 3，最终保留：

```text
子问题1：A
子问题2：C
子问题3：E
```

面试表达：

> `capTotalIntents` 是一层全局保护。它避免多子问题场景下意图数量过多，导致后续检索和工具调用发散。它既控制总量，又保证每个子问题的主意图不被裁掉。

## 7. 得到每个子问题的意图之后做什么

得到 `subIntents` 后，主链路继续执行：

```text
resolveIntents
  -> handleGuidance
  -> handleSystemOnly
  -> retrieve
  -> handleEmptyRetrieval
  -> streamRagResponse
```

具体含义：

1. `handleGuidance`：如果多个相似意图都可能命中，先进行歧义引导。
2. `handleSystemOnly`：如果所有子问题都是 SYSTEM-only，不查知识库，直接系统回答。
3. `retrieve`：进入 `RetrievalEngine`，执行知识检索和 MCP 工具调用。
4. `streamRagResponse`：把检索上下文组装进 Prompt，调用大模型生成流式回答。

`RetrievalEngine` 会把意图拆成：

- 知识类意图：进入多通道检索。
- MCP 意图：进入工具调用。

## 8. 简历表述如何优化

原始表述中“意图定向检索与全局向量检索双路召回”容易让人理解成每次固定两路都跑，但代码里全局向量检索是按置信度条件启用的。

推荐简历表达：

> 实现基于意图识别的多通道检索引擎，根据问题场景定向检索对应知识库，并在低置信场景下启用全局向量检索兜底；通过 `CompletableFuture` 并行执行多路检索，结合去重融合与 Rerank 精排，提升召回覆盖率和结果相关性。

避免在简历中直接写 `KB`、`Collection`，因为面试官不一定熟悉这些实现名词。可以在面试展开时再解释：

- `KB`：知识库。
- `Collection`：向量库中的一批知识向量数据。

## 9. 什么是多通道检索引擎

多通道检索引擎是项目自己封装的一层检索编排器，不是向量数据库自带能力。

核心类：`MultiChannelRetrievalEngine`。

它做三件事：

1. 选择当前请求需要启用哪些检索通道。
2. 使用 `CompletableFuture` 并行执行这些通道。
3. 合并多个通道结果，并统一执行去重和 Rerank。

当前主要通道：

- `IntentDirectedSearchChannel`：意图定向检索。
- `VectorGlobalSearchChannel`：全局向量检索。

关系：

```text
MultiChannelRetrievalEngine
  ├── IntentDirectedSearchChannel
  ├── VectorGlobalSearchChannel
  └── PostProcessors
      ├── DeduplicationPostProcessor
      └── RerankPostProcessor
```

面试表达：

> 多通道检索引擎是检索阶段的总调度器。它把不同检索策略抽象成 `SearchChannel`，请求进入后根据上下文判断哪些通道启用，然后并行执行，最后统一合并、去重和重排。

## 10. `searchChannels` 是何时填充的

`MultiChannelRetrievalEngine` 中有：

```java
private final List<SearchChannel> searchChannels;
```

它是由 Spring 容器在创建 `MultiChannelRetrievalEngine` Bean 时自动注入的。

因为以下两个类都实现了 `SearchChannel` 并注册为 Spring Bean：

```java
@Component
public class IntentDirectedSearchChannel implements SearchChannel
```

```java
@Component
public class VectorGlobalSearchChannel implements SearchChannel
```

所以 Spring 会自动收集所有 `SearchChannel` 类型的 Bean，组装成 `List<SearchChannel>` 注入。

面试表达：

> `searchChannels` 是 Spring 的集合注入能力填充的。新增检索通道时，只需要实现 `SearchChannel` 并注册为 Bean，多通道引擎不需要修改代码。

## 11. 何时启用定向检索，何时启用全局检索

是否启用由每个通道的 `isEnabled(context)` 决定。

### 11.1 定向检索启用条件

`IntentDirectedSearchChannel` 的条件：

```text
配置开启
+ 有意图识别结果
+ 存在知识类意图
+ 意图分数 >= minIntentScore
```

默认 `minIntentScore` 是 `0.4`。

含义：

> 当系统识别出比较相关的知识类意图时，就根据这个意图定向查对应知识范围。

### 11.2 全局检索启用条件

`VectorGlobalSearchChannel` 的条件：

```text
配置开启
+ 满足以下任一条件：
  1. 没有识别出任何意图
  2. 最高意图分数 < 0.6
  3. 只识别出 1 个意图，且最高分 < 0.8
```

含义：

> 当系统对意图不够有把握时，启用全局向量检索兜底。

### 11.3 两者会不会同时启用

会。

例如只有一个知识类意图，分数是 `0.7`：

```text
定向检索：0.7 >= 0.4，启用
全局检索：只有一个意图且 0.7 < 0.8，启用
```

最终两个通道会并行执行，结果再统一去重和 Rerank。

## 12. 为什么这样设置分数阈值

这些阈值不是直接决定最终答案，而是控制召回策略。

### 12.1 定向检索阈值 0.4

定向检索阈值较低，是为了减少漏召回。

原因：

- 定向检索只是召回候选，不是最终结果。
- 后面还有去重和 Rerank。
- 中等相关的意图也值得进入候选召回。

### 12.2 全局检索阈值 0.6

最高意图分数低于 `0.6` 时，说明系统对意图判断不够可靠，需要扩大检索范围兜底。

### 12.3 单意图补充阈值 0.8

如果只识别出一个意图且分数低于 `0.8`，系统会保守地同时启用全局检索。

原因：

- 只有一个候选时缺少对比。
- 这个意图可能是误判。
- 全局检索可以补充召回，降低漏召回风险。

面试表达：

> 定向检索阈值偏低，是为了让中等相关意图也进入召回；全局检索阈值是为了在意图置信度不足时兜底；单意图补充阈值则是为了处理只有一个中等置信意图时的误判风险。

## 13. `executeSearchChannels` 中的 `CompletableFuture` 在做什么

核心代码：

```java
List<CompletableFuture<SearchChannelResult>> futures = enabledChannels.stream()
        .map(channel -> CompletableFuture.supplyAsync(
                () -> {
                    try {
                        log.info("执行检索通道：{}", channel.getName());
                        return channel.search(context);
                    } catch (Exception e) {
                        log.error("检索通道 {} 执行失败", channel.getName(), e);
                        return emptyResult(channel);
                    }
                },
                ragRetrievalExecutor
        ))
        .toList();
```

含义：

1. 遍历所有已启用的检索通道。
2. 对每个通道创建一个异步任务。
3. 在线程池 `ragRetrievalExecutor` 中执行 `channel.search(context)`。
4. 每个任务返回一个 `SearchChannelResult`。
5. 如果某个通道失败，降级为空结果，不影响其他通道。

如果启用了两个通道：

```text
IntentDirectedSearchChannel
VectorGlobalSearchChannel
```

那么会创建两个异步任务：

```text
任务1：执行意图定向检索
任务2：执行全局向量检索
```

`.map(...)` 的作用是把每个 `SearchChannel` 转换成一个 `CompletableFuture<SearchChannelResult>`。

## 14. 两个检索通道的 `search` 做什么

### 14.1 意图定向检索的 `search`

核心流程：

1. 从上下文中提取知识类意图。
2. 按 `minIntentScore` 过滤。
3. 读取 `topKMultiplier`。
4. 调用 `retrieveByIntents`。
5. 返回 `SearchChannelResult`。

关键代码：

```java
int topKMultiplier = properties.getChannels().getIntentDirected().getTopKMultiplier();
List<RetrievedChunk> allChunks = retrieveByIntents(
        context.getMainQuestion(),
        kbIntents,
        context.getTopK(),
        topKMultiplier
);
```

### 14.2 全局向量检索的 `search`

核心流程：

1. 查询系统中所有可检索知识范围。
2. 读取全局检索 `topKMultiplier`。
3. 对所有知识范围并行执行向量检索。
4. 返回 `SearchChannelResult`。

关键代码：

```java
List<String> collections = getAllKBCollections();
List<RetrievedChunk> allChunks = retrieveFromAllCollections(
        context.getMainQuestion(),
        collections,
        context.getTopK() * topKMultiplier
);
```

## 15. 两种检索具体如何检索

两种检索底层都是向量检索，区别在于检索范围不同。

### 15.1 意图定向检索

意图定向检索会根据命中的意图节点确定检索范围：

```java
retrieverService.retrieve(
        RetrieveRequest.builder()
                .collectionName(node.getCollectionName())
                .query(question)
                .topK(task.intentTopK())
                .build()
);
```

含义：

```text
在当前意图绑定的知识范围里，
用当前问题检索 topK 条相似内容。
```

### 15.2 全局向量检索

全局向量检索会获取所有知识范围，然后对每个范围都执行：

```java
retrieverService.retrieve(
        RetrieveRequest.builder()
                .collectionName(collectionName)
                .query(question)
                .topK(topK)
                .build()
);
```

含义：

```text
在每一个知识范围里，
都用当前问题做向量检索。
```

## 16. `query` 是什么

`query` 指的是拿去做检索的文本问题。

在这个链路里，它通常是改写拆分后的子问题。

示例：

```text
用户原始问题：
OA 系统的数据安全规范是什么？顺便介绍一下保险系统的数据安全要求。

拆分后：
子问题1：OA 系统的数据安全规范是什么？
子问题2：保险系统的数据安全要求是什么？
```

处理子问题1时：

```text
query = OA 系统的数据安全规范是什么？
```

处理子问题2时：

```text
query = 保险系统的数据安全要求是什么？
```

底层会把 `query` 转成 embedding 向量，再去向量数据库做相似度匹配。

## 17. 检索范围是什么

检索范围指的是：这次向量检索要在哪一批知识内容里查。

代码层面就是 `collectionName`。

可以理解成：

```text
query = 查什么
collectionName = 去哪里查
topK = 查多少条
```

示例：

```text
oa_security        -> OA 系统数据安全文档
insurance_security -> 保险系统数据安全文档
hr_policy          -> 人事制度文档
finance_invoice    -> 财务开票信息文档
```

如果用户问：

```text
OA 系统的数据安全规范是什么？
```

命中意图绑定的范围是：

```text
collectionName = oa_security
```

那么定向检索就只在 OA 系统数据安全相关文档里查。

全局向量检索则会遍历所有知识范围。

## 18. `retrieverService.retrieve(...)` 做什么

代码：

```java
return retrieverService.retrieve(
        RetrieveRequest.builder()
                .collectionName(node.getCollectionName())
                .query(question)
                .topK(task.intentTopK())
                .build()
);
```

含义：

1. 构造一个 `RetrieveRequest`。
2. 指定检索范围 `collectionName`。
3. 指定检索文本 `query`。
4. 指定召回数量 `topK`。
5. 交给底层 `RetrieverService` 做向量检索。

一句话：

> 根据当前命中的意图节点，构造一个“在该意图对应知识范围内，用当前问题检索若干条候选片段”的请求，然后交给底层向量检索服务。

## 19. `retrieveByVector(norm, retrieveParam)` 做什么

`retrieveByVector` 是真正拿 query 向量去向量数据库查相似文档片段的步骤。

前置流程：

```java
List<Float> emb = embeddingService.embed(retrieveParam.getQuery());
float[] vec = toArray(emb);
float[] norm = normalize(vec);
return retrieveByVector(norm, retrieveParam);
```

含义：

```text
用户问题文本
  -> 调 embedding 模型转成向量
  -> 转成 float[]
  -> 向量归一化
  -> 用向量去数据库检索
```

以 Milvus 为例：

```java
SearchReq req = SearchReq.builder()
        .collectionName(retrieveParam.getCollectionName())
        .annsField("embedding")
        .data(vectors)
        .topK(retrieveParam.getTopK())
        .outputFields(List.of("id", "content", "metadata"))
        .build();

SearchResp resp = milvusClient.search(req);
```

含义：

- `collectionName`：查哪个知识范围。
- `annsField("embedding")`：向量字段。
- `data(vectors)`：当前问题向量。
- `topK`：返回多少条相似结果。
- `outputFields`：返回片段 id、文本内容、元数据。

最后封装成：

```java
RetrievedChunk(id, text, score)
```

如果使用 pgvector，则 SQL 大致是：

```sql
SELECT id, content, 1 - (embedding <=> ?::vector) AS score
FROM t_knowledge_vector
WHERE metadata->>'collection_name' = ?
ORDER BY embedding <=> ?::vector
LIMIT ?
```

含义：

> 在指定知识范围下，按向量距离从近到远排序，取前 topK 条文档片段。

## 20. 从两种检索并行 search 到向量库返回的完整流程

完整流程：

```text
MultiChannelRetrievalEngine
  -> 筛选启用的检索通道
  -> CompletableFuture 并行执行 channel.search(context)
      -> IntentDirectedSearchChannel.search
          -> 提取知识类意图
          -> 每个意图并行检索对应知识范围
          -> query 转 embedding
          -> 去向量数据库查相似片段
          -> 返回 RetrievedChunk 列表
      -> VectorGlobalSearchChannel.search
          -> 获取所有知识范围
          -> 每个知识范围并行检索
          -> query 转 embedding
          -> 去向量数据库查相似片段
          -> 返回 RetrievedChunk 列表
  -> join 等待所有通道完成
  -> 合并通道结果
  -> Deduplication 去重
  -> Rerank 精排
  -> 返回最终 Chunk
```

一句话版：

> 多通道检索引擎先筛出本次启用的检索通道，并用 `CompletableFuture` 并行执行。定向检索根据命中的意图节点去对应知识范围查；全局检索遍历所有知识范围兜底查。两个通道底层都会把 query 转成 embedding，再带着检索范围和 topK 去 Milvus 或 pgvector 做向量相似度搜索，返回 `RetrievedChunk`。最后引擎统一合并、去重和 Rerank。

## 21. 去重融合和 Rerank 精排是在什么时候做的

去重融合和 Rerank 精排都发生在多通道检索完成之后。

主流程在 `MultiChannelRetrievalEngine#retrieveKnowledgeChannels`：

```java
public List<RetrievedChunk> retrieveKnowledgeChannels(List<SubQuestionIntent> subIntents, int topK) {
    SearchContext context = buildSearchContext(subIntents, topK);

    // 第一阶段：并行执行意图定向检索、全局向量检索等通道
    List<SearchChannelResult> channelResults = executeSearchChannels(context);
    if (CollUtil.isEmpty(channelResults)) {
        return List.of();
    }

    // 第二阶段：对所有通道的结果统一做后处理
    // 去重融合和 Rerank 精排都在这里执行
    return executePostProcessors(channelResults, context);
}
```

后处理链在 `executePostProcessors` 中执行：

```java
List<SearchResultPostProcessor> enabledProcessors = postProcessors.stream()
        .filter(processor -> processor.isEnabled(context))
        .sorted(Comparator.comparingInt(SearchResultPostProcessor::getOrder))
        .toList();

List<RetrievedChunk> chunks = results.stream()
        .flatMap(r -> r.getChunks().stream())
        .collect(Collectors.toList());

for (SearchResultPostProcessor processor : enabledProcessors) {
    chunks = processor.process(chunks, results, context);
}
```

当前主要处理器顺序：

```text
DeduplicationPostProcessor  order = 1
RerankPostProcessor         order = 10
```

所以流程一定是：

```text
多通道并行检索
  -> 合并所有通道 Chunk
  -> 先去重融合
  -> 再 Rerank 精排
  -> 返回最终 TopK
```

面试表达：

> 去重和 Rerank 是多通道检索后的后处理链。引擎先并行执行各检索通道，拿到多个 `SearchChannelResult` 后，把所有 Chunk 合并成候选列表，再按处理器 order 先去重、再精排。

## 22. 去重融合具体做了什么

去重处理器是 `DeduplicationPostProcessor`。

关键逻辑：

```java
Map<String, RetrievedChunk> chunkMap = new LinkedHashMap<>();

results.stream()
        .sorted((r1, r2) -> Integer.compare(
                getChannelPriority(r1.getChannelType()),
                getChannelPriority(r2.getChannelType())
        ))
        .forEach(result -> {
            for (RetrievedChunk chunk : result.getChunks()) {
                String key = generateChunkKey(chunk);

                if (!chunkMap.containsKey(key)) {
                    chunkMap.put(key, chunk);
                } else {
                    RetrievedChunk existing = chunkMap.get(key);
                    if (chunk.getScore() > existing.getScore()) {
                        chunkMap.put(key, chunk);
                    }
                }
            }
        });
```

去重 key：

```java
private String generateChunkKey(RetrievedChunk chunk) {
    return chunk.getId() != null
            ? chunk.getId()
            : String.valueOf(chunk.getText().hashCode());
}
```

含义：

- 有 `chunk.id` 时，按 `id` 判断是否重复。
- 没有 `id` 时，按文本 hash 判断是否重复。
- 如果定向检索和全局检索命中了同一个 Chunk，就比较两条结果的 `score`。
- 保留分数更高的那条。

示例：

```text
定向检索返回：
chunkId=123, score=0.82

全局检索返回：
chunkId=123, score=0.76

去重后保留：
chunkId=123, score=0.82
```

面试表达：

> 定向检索和全局检索可能命中同一个 Chunk。去重处理器会用 Chunk ID 或内容 hash 判断重复，重复时保留分数更高的结果，这样既避免 Prompt 里出现重复片段，也保留更可靠的候选分数。

## 23. Chunk 的分数是什么时候生成的

Chunk 分数有两个阶段来源：

```text
向量检索阶段：生成初始相似度分数
Rerank 阶段：可能用 relevance_score 覆盖原分数
```

### 23.1 向量检索阶段的初始分数

如果使用 Milvus，分数来自 Milvus 搜索结果：

```java
return results.get(0).stream()
        .map(r -> new RetrievedChunk(
                Objects.toString(r.getEntity().get("id"), ""),
                Objects.toString(r.getEntity().get("content"), ""),
                r.getScore()))
        .collect(Collectors.toList());
```

这里的 `r.getScore()` 就是向量数据库返回的相似度分数。

如果使用 pgvector，分数由 SQL 计算：

```sql
SELECT id, content, 1 - (embedding <=> ?::vector) AS score
FROM t_knowledge_vector
WHERE metadata->>'collection_name' = ?
ORDER BY embedding <=> ?::vector
LIMIT ?
```

这里用 `1 - 向量距离` 转成相似度分数。

### 23.2 Rerank 阶段的分数

Rerank 服务返回结果后，如果有 `relevance_score`，代码会重新构造 `RetrievedChunk`：

```java
Float score = null;
if (item.has("relevance_score") && !item.get("relevance_score").isJsonNull()) {
    score = item.get("relevance_score").getAsFloat();
}

RetrievedChunk hit = score != null ? new RetrievedChunk(src.getId(), src.getText(), score) : src;
```

含义：

```text
Rerank 前：
chunk.score = 向量相似度分数

Rerank 后：
chunk.score = Rerank 模型返回的 relevance_score
```

面试表达：

> Chunk 的初始分数是在向量检索返回时生成的，Milvus 来自 `SearchResult#getScore()`，pgvector 通过 SQL 计算相似度。去重阶段使用这个初始分数判断保留哪条重复结果。Rerank 阶段如果模型返回 `relevance_score`，会用新的相关性分数覆盖原来的向量分数。

## 24. Rerank 模型是不是专门做重排的

是的。这里调用的是专门的 Rerank 模型，不是普通聊天模型。

在 `RoutingRerankService` 中，调用模型时指定能力类型：

```java
executor.executeWithFallback(
        ModelCapability.RERANK,
        selector.selectRerankCandidates(),
        ...
)
```

在 `BaiLianRerankClient#doRerank` 中，请求 URL 也是按照 Rerank 能力解析：

```java
.url(ModelUrlResolver.resolveUrl(provider, target.candidate(), ModelCapability.RERANK))
```

它的输入不是聊天消息，而是：

```text
query：用户问题
documents：前面召回的一批候选 Chunk 文本
top_n：希望返回多少条
```

它的输出是：

```text
候选文档的新排序
+ 每个候选与 query 的 relevance_score
```

面试表达：

> 这里用的是专门的 Rerank 模型。向量检索负责粗召回，可能召回一批相关但排序不够精准的 Chunk；Rerank 模型接收 query 和候选 Chunk 文本，重新计算相关性，返回排序后的 topN 和相关性分数。

## 25. Rerank 请求输入示例

用户问题：

```text
OA 系统的数据安全规范是什么？
```

向量检索候选 Chunk：

```text
候选1：OA 系统应对用户登录、权限变更、数据导出等操作进行审计记录，并保留不少于 180 天。
候选2：互联网保险系统的数据传输应使用 HTTPS，并对敏感字段进行加密存储。
候选3：OA 系统中的员工通讯录、审批记录等数据应按照内部数据分级制度进行访问控制。
候选4：财务开票信息包括公司名称、税号、开户行及银行账号。
候选5：OA 系统应建立数据备份和恢复机制，关键业务数据每日备份。
```

发给 Rerank 模型的请求大概是：

```json
{
  "model": "gte-rerank-v2",
  "input": {
    "query": "OA 系统的数据安全规范是什么？",
    "documents": [
      "OA 系统应对用户登录、权限变更、数据导出等操作进行审计记录，并保留不少于 180 天。",
      "互联网保险系统的数据传输应使用 HTTPS，并对敏感字段进行加密存储。",
      "OA 系统中的员工通讯录、审批记录等数据应按照内部数据分级制度进行访问控制。",
      "财务开票信息包括公司名称、税号、开户行及银行账号。",
      "OA 系统应建立数据备份和恢复机制，关键业务数据每日备份。"
    ]
  },
  "parameters": {
    "top_n": 3,
    "return_documents": true
  }
}
```

可能返回：

```json
{
  "output": {
    "results": [
      {
        "index": 2,
        "relevance_score": 0.94
      },
      {
        "index": 0,
        "relevance_score": 0.91
      },
      {
        "index": 4,
        "relevance_score": 0.87
      }
    ]
  }
}
```

其中 `index` 表示原始 `documents` 数组下标：

```text
index=2 -> 原候选3
index=0 -> 原候选1
index=4 -> 原候选5
```

代码会根据 `index` 找回原始 `RetrievedChunk`：

```java
int idx = item.get("index").getAsInt();
RetrievedChunk src = candidates.get(idx);
```

再用 `relevance_score` 更新分数：

```java
RetrievedChunk hit = score != null ? new RetrievedChunk(src.getId(), src.getText(), score) : src;
```

## 26. `doRerank` 函数在做什么

`BaiLianRerankClient#doRerank` 的职责是：

> 把去重后的候选 Chunk 文本发给百炼 Rerank 接口，让模型根据 query 重新排序并打相关性分，最后返回 topN 条更相关的 Chunk。

核心流程：

```text
解析 provider 配置
  -> 组装请求 JSON
  -> 构造 HTTP POST Request
  -> 调用百炼 Rerank 接口
  -> 解析 output.results
  -> 根据 index 找回原候选 Chunk
  -> 用 relevance_score 更新 score
  -> 返回 topN
  -> 如果返回不足 topN，用原候选补齐
```

请求体组装：

```java
JsonObject reqBody = new JsonObject();
reqBody.addProperty("model", HttpResponseHelper.requireModel(target, provider()));

JsonObject input = new JsonObject();
input.addProperty("query", query);

JsonArray documentsArray = new JsonArray();
for (RetrievedChunk each : candidates) {
    documentsArray.add(each.getText() == null ? "" : each.getText());
}
input.add("documents", documentsArray);

JsonObject parameters = new JsonObject();
parameters.addProperty("top_n", topN);
parameters.addProperty("return_documents", true);

reqBody.add("input", input);
reqBody.add("parameters", parameters);
```

构造 HTTP 请求：

```java
Request request = new Request.Builder()
        .url(ModelUrlResolver.resolveUrl(provider, target.candidate(), ModelCapability.RERANK))
        .post(RequestBody.create(reqBody.toString(), HttpMediaTypes.JSON))
        .addHeader("Authorization", "Bearer " + provider.getApiKey())
        .build();
```

发送请求：

```java
try (Response response = httpClient.newCall(request).execute()) {
    ...
}
```

解析排序结果：

```java
JsonArray results = output.getAsJsonArray("results");

for (JsonElement elem : results) {
    JsonObject item = elem.getAsJsonObject();
    int idx = item.get("index").getAsInt();
    RetrievedChunk src = candidates.get(idx);

    Float score = null;
    if (item.has("relevance_score") && !item.get("relevance_score").isJsonNull()) {
        score = item.get("relevance_score").getAsFloat();
    }

    RetrievedChunk hit = score != null ? new RetrievedChunk(src.getId(), src.getText(), score) : src;
    reranked.add(hit);
}
```

面试表达：

> `doRerank` 先把 query 和候选 Chunk 文本组装成 Rerank 请求，调用百炼 Rerank 接口。接口返回排序后的 results，每个结果里有原候选数组的 index 和 relevance_score。代码根据 index 找回原始 Chunk，再用 relevance_score 替换原来的向量分数，最终返回 topN 条。如果 Rerank 返回不足 topN，则用原候选顺序补齐。

## 27. `Request.Builder` 每一步在做什么

代码：

```java
Request request = new Request.Builder()
        .url(ModelUrlResolver.resolveUrl(provider, target.candidate(), ModelCapability.RERANK))
        .post(RequestBody.create(reqBody.toString(), HttpMediaTypes.JSON))
        .addHeader("Authorization", "Bearer " + provider.getApiKey())
        .build();
```

逐行解释：

```java
new Request.Builder()
```

创建 OkHttp 的 HTTP 请求构建器。

```java
.url(ModelUrlResolver.resolveUrl(provider, target.candidate(), ModelCapability.RERANK))
```

动态解析 Rerank 接口 URL。它根据：

- 模型供应商配置 `provider`
- 当前路由选中的候选模型 `target.candidate()`
- 能力类型 `ModelCapability.RERANK`

拼出最终请求地址。

```java
.post(RequestBody.create(reqBody.toString(), HttpMediaTypes.JSON))
```

指定这是 POST 请求，并把前面组装好的 JSON 请求体发出去。

```java
.addHeader("Authorization", "Bearer " + provider.getApiKey())
```

添加 API Key 鉴权头。

```java
.build()
```

生成最终不可变的 `Request` 对象。

真正发送请求是在：

```java
httpClient.newCall(request).execute()
```

## 28. `httpClient.newCall(request)` 是什么

`httpClient.newCall(request)` 是 OkHttp 的用法，作用是根据 `Request` 创建一个可执行的 HTTP 调用对象。

可以拆成：

```java
Call call = httpClient.newCall(request);
Response response = call.execute();
```

含义：

```text
request：描述这次请求，包括 URL、POST body、headers
newCall：创建一次待执行的 HTTP 调用
execute：同步发送请求并等待响应
```

在 Rerank 这里就是：

```text
query + candidates
  -> 组装 HTTP POST Request
  -> newCall 创建调用
  -> execute 发送给百炼 Rerank 接口
  -> 返回 Response
  -> 解析 output.results
```

`try (Response response = ...)` 是 try-with-resources，会自动关闭响应资源，避免连接泄漏。

## 29. 什么是 Cross-Encoder

Cross-Encoder 是一种常用于 Rerank 精排的模型结构。

它的核心特点是：

> 把 query 和候选文档放在一起输入模型，让模型直接判断二者相关性。

示例输入：

```text
[CLS] OA 系统的数据安全规范是什么？ [SEP]
OA 系统应对用户登录、权限变更、数据导出等操作进行审计记录。 [SEP]
```

模型会同时看到问题和文档内容，通过注意力机制让两边充分交互，然后输出相关性分数：

```text
relevance_score = 0.94
```

它和向量检索常用的 Bi-Encoder 不同：

```text
Bi-Encoder：
  query 单独编码成向量
  document 单独编码成向量
  再算向量相似度
  优点：快，适合海量粗召回
  缺点：相关性判断没那么细

Cross-Encoder：
  query 和 document 一起输入模型
  直接输出相关性分数
  优点：判断更准
  缺点：慢，不适合全库搜索
```

RAG 中常见组合：

```text
Bi-Encoder / 向量检索：
  从海量文档中快速粗召回几十条

Cross-Encoder / Rerank：
  对这几十条候选精排，选出最相关的 topN
```

面试表达：

> Cross-Encoder 会把 query 和候选文档一起输入模型，让二者在模型内部充分交互，然后输出相关性分数。它比向量相似度更准，但计算成本更高，所以通常不会用于全库检索，而是用在向量检索之后，对少量候选 Chunk 做 Rerank 精排。
