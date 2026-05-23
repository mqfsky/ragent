# 会话记忆管理机制面试笔记

日期：2026-05-20

## 简历表述

实现会话记忆管理机制，采用滑动窗口保留最近 N 轮对话，超过阈值后异步生成摘要并持久化至 PostgreSQL；加载上下文时并行读取历史消息与摘要，在保留关键上下文的同时控制长对话 Token 成本。

## PostgreSQL 是什么

PostgreSQL 是一个开源关系型数据库，常简称 Postgres。它和 MySQL 一样支持 SQL、事务、索引、权限控制等关系型数据库能力。

在本项目中，PostgreSQL 主要用于保存结构化业务数据，例如：

- `t_user`：用户信息。
- `t_conversation`：会话列表和会话元信息。
- `t_message`：用户和助手的原始对话消息。
- `t_conversation_summary`：会话压缩摘要。
- 知识库元数据、Pipeline 配置、Trace 数据等。

面试可以说：

> PostgreSQL 是项目里的关系型持久化数据库，用来保存会话消息、摘要、用户、知识库元数据等结构化数据。会话摘要持久化到 PostgreSQL，是为了让长对话的压缩记忆可以跨请求、跨服务重启保留下来。

## 为什么不用 MySQL

会话消息和摘要本身用 MySQL 也可以，因为它们本质上是关系型数据。但项目选择 PostgreSQL，主要是因为 PostgreSQL 的扩展能力更强，尤其可以通过 `pgvector` 支持向量存储和相似度检索。

面试不要说 MySQL 不行，更稳的说法是：

> 如果只是做会话消息和摘要存储，MySQL 完全可以。但这个项目整体是 RAG 场景，除了结构化数据，还涉及知识库向量检索。PostgreSQL 配合 pgvector 能把用户、会话、摘要、知识库元数据和部分向量能力放在同一个数据库体系里，架构上更统一。

## PostgreSQL 语法示例

PostgreSQL 也是 SQL 体系，基本建表、查询、索引、事务语法和 MySQL 类似。

项目中的摘要表：

```sql
CREATE TABLE t_conversation_summary (
    id              VARCHAR(20) NOT NULL PRIMARY KEY,
    conversation_id VARCHAR(20) NOT NULL,
    user_id         VARCHAR(20) NOT NULL,
    last_message_id VARCHAR(20) NOT NULL,
    content         TEXT        NOT NULL,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT DEFAULT 0
);
```

查询最新摘要：

```sql
SELECT *
FROM t_conversation_summary
WHERE conversation_id = 'xxx'
  AND user_id = 'u001'
  AND deleted = 0
ORDER BY id DESC
LIMIT 1;
```

PostgreSQL 常见特点：

- 文本大字段常用 `TEXT`。
- 时间常用 `TIMESTAMP`。
- 分页支持 `LIMIT`。
- 注释常用 `COMMENT ON TABLE` 和 `COMMENT ON COLUMN`。
- 支持 JSON、数组、全文检索、pgvector 等扩展能力。

## 会话管理机制整体实现

这套机制可以按四件事理解：

1. 消息持久化：每轮 user 和 assistant 消息都会写入 `t_message`。
2. 滑动窗口：加载上下文时只保留最近 N 轮原文消息。
3. 摘要压缩：窗口外的旧消息异步压缩成摘要，写入 `t_conversation_summary`。
4. Prompt 组装：请求模型时使用“系统提示词 + 摘要 + 最近历史 + 当前问题 + 检索证据”。

短期记忆是最近 N 轮原文对话，长期记忆是历史摘要。

面试可以说：

> 我把会话记忆分成短期记忆和长期记忆。短期记忆用滑动窗口保留最近 N 轮原文对话，保证模型理解当前上下文；长期记忆把窗口外的旧对话异步压缩成摘要并持久化到 PostgreSQL。每次请求时并行读取摘要和最近历史，再把摘要作为 system message 拼入 Prompt。这样既能保留长对话里的关键信息，又能控制 Token 成本和响应延迟。

## 为什么要有会话列表

`t_conversation` 是会话元信息表，不存完整聊天内容。完整消息存在 `t_message`。

会话列表主要用于：

- 展示用户历史会话。
- 按最近活跃时间排序。
- 存会话标题。
- 用户点击会话后，用 `conversation_id` 恢复消息列表。
- 用 `conversation_id + user_id` 做用户隔离。

如果没有 `t_conversation`，每次展示历史会话都要从 `t_message` 里分组、聚合、取最新消息、排序，成本更高。

面试可以说：

> `t_conversation` 是会话元信息表，`t_message` 是消息明细表。会话列表用于展示历史会话、维护标题和最近活跃时间、支持点击恢复上下文。如果没有这张表，每次展示历史会话都要从消息表聚合，成本更高，也不利于分页排序和用户隔离。

## 系统如何加载历史

入口在 `StreamChatPipeline#loadMemory`：

```java
private void loadMemory(StreamChatContext ctx) {
    List<ChatMessage> history = memoryService.loadAndAppend(
            ctx.getConversationId(),
            ctx.getUserId(),
            ChatMessage.user(ctx.getQuestion())
    );
    ctx.setHistory(history);
}
```

`loadAndAppend` 的当前实现是先加载历史，再追加当前用户消息：

```java
default List<ChatMessage> loadAndAppend(String conversationId, String userId, ChatMessage message) {
    // 加载本轮请求之前的历史上下文
    List<ChatMessage> history = load(conversationId, userId);

    // 把当前用户问题写入消息表，供下一轮对话使用
    append(conversationId, userId, message);

    return history;
}
```

当前用户问题没有作为历史返回，因为后续 Prompt 组装会把当前问题单独作为最新 `user message` 加进去。

真正的加载逻辑在 `DefaultConversationMemoryService#load`。它会并行加载摘要和历史消息：

```java
CompletableFuture<ChatMessage> summaryFuture = CompletableFuture.supplyAsync(
        () -> loadSummaryWithFallback(conversationId, userId),
        memoryLoadExecutor
);

CompletableFuture<List<ChatMessage>> historyFuture = CompletableFuture.supplyAsync(
        () -> loadHistoryWithFallback(conversationId, userId),
        memoryLoadExecutor
);

return CompletableFuture.allOf(summaryFuture, historyFuture)
        .thenApply(v -> {
            ChatMessage summary = summaryFuture.join();
            List<ChatMessage> history = historyFuture.join();
            return attachSummary(summary, history);
        })
        .join();
```

历史消息加载在 `JdbcConversationMemoryStore#loadHistory`：

```java
public List<ChatMessage> loadHistory(String conversationId, String userId) {
    // N 轮对话 = N 条 user + N 条 assistant，所以乘以 2
    int maxMessages = resolveMaxHistoryMessages();

    // 从数据库读取最近 maxMessages 条消息
    List<ConversationMessageVO> dbMessages = conversationMessageService.listMessages(
            conversationId,
            userId,
            maxMessages,
            ConversationMessageOrder.DESC
    );

    if (CollUtil.isEmpty(dbMessages)) {
        return List.of();
    }

    List<ChatMessage> result = dbMessages.stream()
            // 数据库 VO 转成模型请求里的 ChatMessage
            .map(this::toChatMessage)
            // 只保留 user / assistant，并过滤空内容
            .filter(this::isHistoryMessage)
            .collect(Collectors.toList());

    // 避免历史上下文从 assistant 消息开始
    return normalizeHistory(result);
}
```

`N` 来自配置：

```yaml
rag:
  memory:
    history-keep-turns: 4
```

所以当前最多加载：

```text
4 轮 * 2 = 8 条消息
```

`normalizeHistory` 的作用是避免截断后第一条是 assistant：

```java
private List<ChatMessage> normalizeHistory(List<ChatMessage> messages) {
    int start = 0;

    // 如果第一条是 assistant，说明它对应的 user 问题被窗口裁掉了
    while (start < messages.size()
            && messages.get(start).getRole() == ChatMessage.Role.ASSISTANT) {
        start++;
    }

    if (start >= messages.size()) {
        return List.of();
    }

    // 保证历史从 user 开始
    return messages.subList(start, messages.size());
}
```

最终 Prompt 中的上下文大致是：

```text
system: 系统提示词
system: <conversation-summary>历史摘要</conversation-summary>
user: 最近历史问题
assistant: 最近历史回答
user: 当前问题 + 检索证据
```

## CompletableFuture 是什么

`CompletableFuture` 是 Java 8 提供的异步编程工具。

可以理解成：

> 把一个耗时任务丢到另一个线程里执行，主流程不用立刻卡住；等结果回来之后，再继续处理结果。

项目里用它并行加载摘要和历史：

```text
线程 A：查询最新会话摘要
线程 B：查询最近 N 轮历史消息
```

因为摘要和历史来自两条独立查询路径，没有强依赖，所以可以并行执行。假设查摘要 40ms、查历史 60ms，串行约 100ms，并行约 60ms。

面试可以说：

> CompletableFuture 是 Java 的异步任务编排工具。这里用它把摘要查询和历史消息查询并行化，因为两者没有先后依赖，等两个 Future 都完成后再合并结果。这样可以降低构造 Prompt 前的等待时间。

## N 应该怎么取

`N` 是 Token 成本、上下文完整性、响应延迟之间的折中参数。

当前配置：

```yaml
history-keep-turns: 4
summary-start-turns: 5
```

取值思路：

- 简单知识库问答，用户问题多数独立，`N=3~5` 通常够用。
- 复杂任务型对话，例如连续改方案、排查问题、写代码，可以提高到 `6~10`。
- RAG 场景还要给检索证据留 Token，所以历史窗口不能太大。
- 如果摘要质量可靠，`N` 可以小一点；如果摘要容易丢约束，`N` 可以稍大一点。
- 线上通过平均 Prompt token、P95 延迟、回答质量和用户追问失败率调优。

面试可以说：

> 我不会把 N 固定理解成越大越好。N 太小会丢掉最近上下文，N 太大会增加 Token 成本和延迟。项目里默认保留 4 轮原文，是因为 RAG 问答还需要给检索证据和系统 Prompt 留空间；更早的内容通过摘要保存关键话题和约束。

## append 在做什么

`DefaultConversationMemoryService#append` 中这行代码：

```java
String messageId = memoryStore.append(conversationId, userId, message);
```

作用是把一条 `ChatMessage` 真正写入数据库，并返回消息 ID。

`memoryStore` 的实现是 `JdbcConversationMemoryStore`，它会把 `ChatMessage` 转成 `ConversationMessageBO`：

```java
ConversationMessageBO conversationMessage = ConversationMessageBO.builder()
        .conversationId(conversationId)
        .userId(userId)
        .role(message.getRole().name().toLowerCase())
        .content(message.getContent())
        .thinkingContent(message.getThinkingContent())
        .thinkingDuration(message.getThinkingDuration())
        .build();
```

然后调用：

```java
String messageId = conversationMessageService.addMessage(conversationMessage);
```

最终插入 `t_message` 表：

```java
public String addMessage(ConversationMessageBO conversationMessage) {
    ConversationMessageDO messageDO = BeanUtil.toBean(conversationMessage, ConversationMessageDO.class);
    conversationMessageMapper.insert(messageDO);
    return messageDO.getId();
}
```

如果是 user 消息，还会创建或更新会话列表：

```java
if (message.getRole() == ChatMessage.Role.USER) {
    conversationService.createOrUpdate(conversation);
}
```

面试可以说：

> `memoryStore.append` 是会话记忆持久化的核心动作。它会把内存里的 `ChatMessage` 转成业务对象，通过 `ConversationMessageService` 写入 PostgreSQL 的 `t_message` 表，并返回数据库生成的消息 ID。如果这条消息是 user 角色，还会同步创建或更新 `t_conversation` 会话记录。

## append 在哪里被调用

主要有四类调用点：

1. 正常用户提问时，通过 `StreamChatPipeline#loadMemory -> loadAndAppend -> append` 保存 user 消息。
2. 模型正常回答完成时，在 `StreamChatEventHandler#onComplete` 保存 assistant 消息。
3. 用户取消流式输出时，如果已有部分回答，会保存部分 assistant 消息。
4. 排队或限流拒绝时，`ChatQueueLimiter` 会保存用户问题和拒绝提示。

真正触发摘要压缩的一般是 assistant 消息 append 之后。

## onComplete 在哪里被调用

`onComplete` 是 `StreamCallback` 的终态回调，主要由模型流式客户端在模型输出结束时触发。

调用链：

```text
RAGChatController
-> RAGChatServiceImpl#streamChat
-> StreamCallbackFactory#createChatEventHandler
-> new StreamChatEventHandler(...)
-> StreamChatPipeline#execute
-> llmService.streamChat(..., callback)
-> AbstractOpenAIStyleChatClient#doStream
-> callback.onComplete()
-> StreamChatEventHandler#onComplete
```

模型客户端解析 SSE 流时，如果解析到完成事件，会调用：

```java
if (event.completed()) {
    callback.onComplete();
    completed = true;
    break;
}
```

进入 `StreamChatEventHandler#onComplete` 后，会把前面 `onContent` 累积的回答保存成 assistant 消息：

```java
ChatMessage message = ChatMessage.assistant(
        answer.toString(),
        thinkingContent,
        resolveThinkingDuration()
);

messageId = memoryService.append(conversationId, userId, message);
```

然后向前端发送 `FINISH` 和 `DONE` 事件，并关闭 SSE。

另外还有两个业务短路场景会主动调用 `callback.onComplete()`：

- 意图歧义，需要引导用户补充问题。
- 没检索到相关文档，直接返回提示。

## 什么时候压缩摘要并入库

摘要压缩发生在 assistant 回答落库之后。

入口：

```java
public String append(String conversationId, String userId, ChatMessage message) {
    String messageId = memoryStore.append(conversationId, userId, message);

    // 消息落库后，尝试触发摘要压缩
    summaryService.compressIfNeeded(conversationId, userId, message);

    return messageId;
}
```

真正判断条件：

```java
public void compressIfNeeded(String conversationId, String userId, ChatMessage message) {
    if (!memoryProperties.getSummaryEnabled()) {
        return;
    }

    // 只在 assistant 回答落库后触发
    if (message.getRole() != ChatMessage.Role.ASSISTANT) {
        return;
    }

    CompletableFuture.runAsync(
            () -> doCompressIfNeeded(conversationId, userId),
            memorySummaryExecutor
    );
}
```

第一层条件：

```text
summary-enabled = true
当前落库消息是 assistant
```

异步任务内部还会判断用户轮数：

```java
long total = conversationGroupService.countUserMessages(conversationId, userId);
if (total < triggerTurns) {
    return;
}
```

当前配置：

```yaml
history-keep-turns: 4
summary-start-turns: 5
summary-enabled: true
summary-max-chars: 200
```

含义：

```text
最近 4 轮保留原文；
从第 5 轮开始，尝试把窗口外的旧消息压缩成摘要。
```

## 为什么要异步生成摘要

摘要不是当前响应的强依赖，它主要服务于下一轮对话的上下文加载。

如果同步生成摘要，本次请求链路会变成：

```text
模型回答完成
-> assistant 消息落库
-> 再调用一次 LLM 生成摘要
-> 摘要入库
-> 前端收到 DONE
```

这样会明显拉长用户感知延迟。

异步后：

```text
assistant 回答落库
-> 当前 SSE 响应结束
-> 后台线程生成摘要并入库
```

面试可以说：

> 摘要压缩不是当前请求返回结果的必要步骤，它服务于后续请求的上下文加载。如果同步生成摘要，会额外增加一次 LLM 调用和一次数据库写入，直接拉长本轮对话耗时。所以项目把它放到独立的摘要线程池异步执行，保证主链路只负责回答生成和消息落库。

## 摘要如何异步生成并入库

异步任务进入 `doCompressIfNeeded` 后主要做五件事。

第一，拿 Redisson 锁，避免同一会话并发重复摘要：

```java
String lockKey = SUMMARY_LOCK_PREFIX + buildLockKey(conversationId, userId);
RLock lock = redissonClient.getLock(lockKey);

if (!lock.tryLock()) {
    return;
}
```

第二，判断是否达到摘要阈值：

```java
long total = conversationGroupService.countUserMessages(conversationId, userId);
if (total < triggerTurns) {
    return;
}
```

第三，读取最新摘要和最近 N 轮 user 消息，确定滑动窗口边界：

```java
ConversationSummaryDO latestSummary =
        conversationGroupService.findLatestSummary(conversationId, userId);

List<ConversationMessageDO> latestUserTurns =
        conversationGroupService.listLatestUserOnlyMessages(
                conversationId,
                userId,
                maxTurns
        );
```

第四，确定要压缩的消息区间：

```text
上一次摘要之后
到最近 N 轮窗口之前
```

第五，调用 LLM 合并旧摘要和待压缩消息，最后写入 PostgreSQL：

```java
ConversationSummaryBO summaryRecord = ConversationSummaryBO.builder()
        .conversationId(conversationId)
        .userId(userId)
        .content(content)
        .lastMessageId(lastMessageId)
        .build();

conversationMessageService.addMessageSummary(summaryRecord);
```

最终插入 `t_conversation_summary`。

## 第 5 轮以后是否每轮都生成摘要

按当前配置：

```yaml
history-keep-turns: 4
summary-start-turns: 5
```

第 5 轮之后，每次 assistant 回答落库都会尝试触发摘要任务。通常每新增一轮，就会有一轮旧对话滑出最近 4 轮窗口，所以通常会生成一次增量摘要。

示例：

```text
第5轮完成：
最近4轮 = 第2~5轮
需要摘要 = 第1轮

第6轮完成：
最近4轮 = 第3~6轮
需要摘要 = 第2轮

第7轮完成：
最近4轮 = 第4~7轮
需要摘要 = 第3轮
```

但不是无条件每次都一定生成。以下情况会跳过：

- 同一会话已有摘要任务在跑，Redisson 锁拿不到。
- 上一次摘要已经覆盖到当前窗口边界。
- 待摘要消息为空。
- 摘要功能关闭。

面试可以说：

> 达到摘要阈值后，每轮 assistant 消息落库都会触发一次异步摘要尝试。因为滑动窗口每前进一轮，就会有一轮旧消息滑出最近 N 轮原文窗口，所以通常会进行一次增量摘要。但代码通过 Redisson 锁、`last_message_id` 和待摘要区间判断避免重复摘要，所以不是无条件每次全量总结。

## 什么叫摘要已经覆盖到当前窗口边界

`cutoffId` 表示最近 N 轮原文窗口中最早的那一条 user 消息，也就是窗口左边界。窗口边界之前的消息才需要进入摘要。

`afterId` 表示上一次摘要已经处理到哪条消息，通常来自 `latestSummary.getLastMessageId()`。

判断逻辑：

```java
if (afterId != null && Long.parseLong(afterId) >= Long.parseLong(cutoffId)) {
    return;
}
```

含义：

```text
如果上一次摘要已经处理到当前窗口边界甚至更靠后，
说明窗口外已经没有未摘要的新消息，
所以不需要重复调用 LLM 生成摘要。
```

举例：

```text
101 user 第1轮
102 assistant 第1轮
103 user 第2轮
104 assistant 第2轮
105 user 第3轮
106 assistant 第3轮
107 user 第4轮
108 assistant 第4轮
109 user 第5轮
110 assistant 第5轮
```

第 5 轮时，最近 4 轮是第 2~5 轮，窗口边界是：

```text
cutoffId = 103
```

如果上一次摘要已经覆盖到：

```text
afterId = 106
```

则：

```text
afterId >= cutoffId
```

说明第 1~3 轮早已被摘要覆盖，而当前窗口外没有新的未摘要消息，所以直接跳过。

## 市面上的对话系统都是这样做吗

不是。

“滑动窗口 + 摘要”是长对话记忆管理的一种常见工程方案，但 DeepSeek、ChatGPT、Claude Code、Codex 等产品不一定都按“第 N 轮后每轮生成摘要”来做，而且它们的内部实现大多不公开。

需要区分两层：

1. 模型 API 通常是无状态的，只看本次请求传入的上下文。
2. 记忆通常由应用层或 Agent 层实现，决定哪些历史、摘要、记忆、项目文件要放进上下文。

商业产品可能会结合：

- token 预算。
- 用户显式保存的长期记忆。
- 对话历史摘要。
- 检索式记忆。
- 上下文缓存。
- 项目文件，例如 `AGENTS.md`、`CLAUDE.md`。
- 达到上下文阈值后的 compact。

面试可以说：

> 不一定所有大模型产品都每轮生成摘要。模型 API 通常是无状态的，长对话记忆是应用层自己管理。我们这个项目采用滑动窗口加增量摘要，是一种工程上容易落地、可解释性强的方案：最近 N 轮保留原文，窗口外的内容异步压缩成摘要。商业产品通常会更复杂，可能结合 token 预算、用户显式记忆、上下文压缩、检索式记忆和缓存机制，不会简单地每轮都摘要。

## 达到上下文阈值后再压缩是怎么做的

有些对话系统不是按轮数压缩，而是按 token budget 触发压缩。

每次请求前先估算：

```text
totalTokens = systemTokens
            + summaryTokens
            + historyTokens
            + evidenceTokens
            + currentQuestionTokens
            + reservedOutputTokens
```

如果超过阈值，就触发压缩：

```text
旧摘要 + 早期历史 -> 新摘要
最近几轮历史 -> 保留原文
当前问题 -> 正常进入 Prompt
```

成熟系统可能分软阈值和硬阈值：

- 软阈值：例如 70%，后台异步摘要。
- 硬阈值：例如 90%，当前请求前必须同步压缩或裁剪。

面试可以说：

> 市面上一些对话系统不会按固定轮数摘要，而是基于 token budget 触发压缩。每次请求前会估算 system prompt、历史消息、检索证据和当前问题的 token 总量。如果超过阈值，就保留最近几轮原文，把更早的历史和旧摘要合并成新摘要，并记录摘要覆盖到的 `last_message_id`。这样摘要频率和真实上下文长度相关。

如果把本项目升级成 token 阈值触发，本质是把：

```java
countUserMessages >= summaryStartTurns
```

改成：

```java
estimatedPromptTokens >= compressThreshold
```

摘要范围仍然可以沿用现在的 `last_message_id + 滑动窗口边界` 设计。

## 没有压缩前如何实现记忆

没有触发压缩前，通常也是把完整对话消息持久化，但要区分：

```text
持久化层：完整保存所有消息
上下文层：每次选择一部分消息放进 Prompt
```

也就是说：

- `t_message` 保存全量原始消息，用于历史展示、审计、重新摘要、问题排查。
- 构造模型 Prompt 时，在 token 预算允许的情况下可以加载完整历史。
- 达到阈值后，才把早期历史压缩成摘要，同时保留最近几轮原文。

摘要不是为了替代原始消息，也不代表删除原始消息。摘要只是为了减少模型输入。

面试可以说：

> 没有触发压缩前，系统一般会把每轮 user、assistant、tool 消息完整持久化到消息表。只要 token 预算允许，就可以把完整历史作为上下文传给模型。达到阈值后，才把早期消息压缩成摘要，同时保留最近几轮原文。摘要只是用于构造模型上下文，不代表删除原始消息；原始消息仍然保存在数据库里，用于历史展示、审计、重新摘要和问题排查。

## 最终记忆法

可以把这套机制记成一句话：

> 原始消息全量入库，Prompt 只带必要上下文；短期靠最近 N 轮原文，长期靠摘要；摘要异步生成，入库记录 `last_message_id`，下次加载时并行读取摘要和最近历史。
