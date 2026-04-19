# Day 12 模型调用、Embedding 与路由

今天进入 `infra-ai` 模块，理解 AI 基础设施层是怎么封装的。

## 今天看哪些文件

- [infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/embedding/RoutingEmbeddingService.java](/Users/mqf/project/ragent/infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/embedding/RoutingEmbeddingService.java)
- `infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/chat/`
- `infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/embedding/`
- `infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/rerank/`
- [bootstrap/src/main/resources/application.yaml](/Users/mqf/project/ragent/bootstrap/src/main/resources/application.yaml)

## 今天重点看什么

### 1. 什么是 AI 基础设施层

你可以先这样理解：

- 上层业务只想“调用模型能力”
- 但不同供应商接口都不同
- 所以需要一层统一封装

### 2. 三种核心能力

今天重点区分：

- `chat`
- `embedding`
- `rerank`

### 3. 为什么要做路由

今天要重点理解：

- 不同模型能力不同
- 成本不同
- 稳定性不同
- 所以系统要支持路由、降级、切换

## 今天的输出

请自己写出：

1. 为什么业务层不应该直接写死某个模型供应商？
2. `embedding` 和 `chat` 在 RAG 里分别负责什么？

## 打卡检查

你今天应该能回答：

- 为什么这类项目很少只接一个模型平台？
- `infra-ai` 模块为什么值得单独拆出来？

## 今天不要做的事

- 不要试图吃透每个 HTTP 请求细节
- 不要因为模型名很多就焦虑

今天只抓统一封装和路由思路。

## 参考答案提纲

### 1. 为什么业务层不应该直接写死某个模型供应商

- 供应商接口各不相同
- 稳定性、能力、成本都可能变化
- 直接写死会让切换成本很高

### 2. `embedding` 和 `chat` 在 RAG 里分别负责什么

- `embedding`：把文本转成向量，用于检索
- `chat`：根据问题和上下文生成最终回答

### 3. 为什么这类项目很少只接一个模型平台

- 为了容错
- 为了成本控制
- 为了不同任务用不同模型

### 4. `infra-ai` 为什么值得单独拆模块

- 因为它是基础设施能力，不应和具体业务紧耦合
- 统一封装后，上层业务会更稳定、更易扩展
