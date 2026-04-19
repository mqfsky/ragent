# Day 13 文档入库流水线

今天看文档从上传到可检索的全过程。

## 今天看哪些文件

- [bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/controller/IngestionTaskController.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/controller/IngestionTaskController.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/service/impl/IngestionTaskServiceImpl.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/service/impl/IngestionTaskServiceImpl.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/engine/IngestionEngine.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/engine/IngestionEngine.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/node/IngestionNode.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/node/IngestionNode.java)
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/node/`

## 今天重点看什么

### 1. 为什么上传文件后不能立刻检索

因为文档要经历一串处理步骤：

- 解析
- 清洗
- 切分
- 向量化
- 写入存储

### 2. 为什么这里设计成 Pipeline

今天重点理解：

- 每一步都可以看成一个节点
- 节点组合起来就是一条处理链
- 这种设计更容易扩展和调试

### 3. `IngestionEngine` 的角色

可以把它理解成：

- 负责驱动整条入库流水线运行的总控

## 今天的输出

请自己画出“文档入库 5 步图”。

## 打卡检查

你今天应该能回答：

- 为什么知识库不是只把整篇文档原样存进去？
- 为什么分块是 RAG 的关键步骤之一？

## 今天不要做的事

- 不要一口气研究所有节点实现细节
- 不要纠结每种文档格式的解析差异

先把整体流水线建立起来。

## 参考答案提纲

### 1. 为什么上传文件后不能立刻检索

- 因为原始文件不能直接用于向量检索
- 必须先经过解析、切分、向量化和入库

### 2. 为什么分块是 RAG 的关键步骤

- 不分块时，文档太大，不适合精确检索
- 分块后更容易找到与问题最相关的片段

### 3. 为什么这里设计成 Pipeline

- 每一步职责清晰
- 方便扩展、替换和调试
- 更适合复杂入库流程

### 4. 文档入库 5 步图参考

- 上传文件
- 解析文本
- 文本切分
- 生成向量
- 写入向量存储和业务表
