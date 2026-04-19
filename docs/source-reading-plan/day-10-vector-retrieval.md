# Day 10 向量检索实现

今天看检索引擎下面的具体实现，理解“知识到底是怎么被搜出来的”。

## 今天看哪些文件

- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/RetrieverService.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/RetrieverService.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/PgRetrieverService.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/PgRetrieverService.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/MilvusRetrieverService.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/MilvusRetrieverService.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/channel/VectorGlobalSearchChannel.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/channel/VectorGlobalSearchChannel.java)

## 今天重点看什么

### 1. 接口与实现的关系

先理解：

- `RetrieverService` 是抽象能力
- `PgRetrieverService` 和 `MilvusRetrieverService` 是不同实现

### 2. 两种向量方案的区别

今天不要求你掌握底层原理，但要知道：

- `pgvector` 是存在 PostgreSQL 里的向量检索方案
- `Milvus` 是专门的向量数据库方案

### 3. 搜索结果是什么

重点关注：

- 输入是问题向量或问题文本
- 输出是相似的知识片段列表

## 今天的输出

请自己写出：

1. 这个项目为什么同时保留 `pgvector` 和 `Milvus` 两种实现？
2. 如果后面要扩展别的向量引擎，应该优先改接口还是具体实现？

## 打卡检查

你今天应该能回答：

- 为什么这个项目的检索层有接口抽象？
- 向量检索的结果最终会被谁消费？

## 今天不要做的事

- 不要纠结数学公式
- 不要深挖每条 SQL 的每个操作符

今天先理解架构和职责。

## 参考答案提纲

### 1. 为什么同时保留 `pgvector` 和 `Milvus`

- 为了支持不同部署和技术选型
- `pgvector` 更适合已有 PostgreSQL 场景
- `Milvus` 更适合专门的向量数据库场景

### 2. 如果扩展别的向量引擎，应优先改哪里

- 优先新增或实现具体实现类
- 尽量保持接口层稳定
- 这正是抽象出 `RetrieverService` 的意义

### 3. 为什么检索层要做接口抽象

- 屏蔽底层向量引擎差异
- 让上层业务不依赖具体实现
- 便于替换和扩展

### 4. 向量检索的结果最终会被谁消费

- 会被检索引擎或聊天主链路消费
- 最终进入 Prompt，上下文再交给模型生成答案
