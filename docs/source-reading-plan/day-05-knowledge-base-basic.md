# Day 05 知识库模块基础 CRUD

今天开始看知识库模块，先把它当成普通业务模块来读。

## 今天看哪些文件

- [bootstrap/src/main/java/com/nageoffer/ai/ragent/knowledge/controller/KnowledgeBaseController.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/knowledge/controller/KnowledgeBaseController.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/knowledge/service/impl/KnowledgeBaseServiceImpl.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/knowledge/service/impl/KnowledgeBaseServiceImpl.java)

## 今天重点看什么

### 1. 知识库模块提供了哪些接口

先扫一遍 Controller，识别：

- 新增知识库
- 编辑知识库
- 查询列表
- 删除或启停

### 2. Service 是怎么组织业务的

看 Service 时重点关注：

- 参数校验
- 唯一性校验
- 数据库落表
- 是否联动了其他组件

### 3. 业务对象长什么样

今天你不用把所有 VO/DTO/DO 都看全，但要注意：

- 请求对象
- 返回对象
- 数据库存储对象

## 今天的输出

请自己总结：

1. 知识库模块最核心的数据是什么？
2. 新增知识库时至少需要哪些字段？
3. 这个模块目前看起来更像“普通后台业务”还是“AI 复杂逻辑”？

## 打卡检查

你今天应该能回答：

- 知识库为什么是整个 RAG 系统的基础？
- 知识库和聊天接口是什么关系？

## 今天不要做的事

- 不要一口气把 `knowledge` 整个目录看完
- 不要提前钻入文档分块或向量写入逻辑

今天先把“知识库管理”当普通业务读懂。

## 参考答案提纲

### 1. 知识库模块最核心的数据是什么

- 知识库本身的信息
- 以及知识库下面挂接的文档信息

### 2. 新增知识库时至少需要哪些字段

- 一般至少会包含知识库名称
- 以及与向量存储或 embedding 相关的基础信息
- 具体字段以 `Controller` 的请求对象和 `Service` 中使用的字段为准

### 3. 这个模块更像普通后台业务还是 AI 复杂逻辑

- 今天这部分更像普通后台业务
- 因为重点是知识库实体的管理，而不是模型推理本身

### 4. 知识库为什么是整个 RAG 系统的基础

- 因为 RAG 的前提是“有可被检索的知识”
- 没有知识库，系统只能依赖模型自身知识，无法回答私有数据问题

### 5. 知识库和聊天接口是什么关系

- 聊天接口会在回答前从知识库中检索相关片段
- 知识库是聊天回答的证据来源之一
