# Day 08 聊天主服务链路

今天把聊天接口继续往下追，进入真正的主业务。

## 今天看哪些文件

- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/RAGChatService.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/RAGChatService.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/impl/RAGChatServiceImpl.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/impl/RAGChatServiceImpl.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/handler/StreamChatEventHandler.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/handler/StreamChatEventHandler.java)

## 今天重点看什么

### 1. 一次聊天请求在 Service 里经历了哪些步骤

你今天要尝试列出顺序，而不是记每一行代码。

例如：

- 接收问题
- 做上下文准备
- 调检索
- 调模型
- 回传流式事件
- 落库存消息

### 2. Handler 起什么作用

今天重点理解：

- 聊天过程中产生的事件是如何被统一发送的

### 3. 主链路不要纠结所有细枝末节

你今天最重要的是抓“大流程”。

## 今天的输出

请自己写一版“聊天主链路 6 步图”。

只要能画出主要阶段就可以，不要求精确到类名。

## 打卡检查

你今天应该能回答：

- 聊天 Service 为什么是项目核心？
- 它和普通问答接口最大的区别是什么？

## 今天不要做的事

- 不要因为某个工具类看不懂就停住
- 不要立刻研究所有事件类型

先把主流程跑通。

## 参考答案提纲

### 1. 聊天主链路可以怎么概括

- 接收用户问题
- 准备上下文和会话信息
- 做检索或工具调用准备
- 调用模型生成
- 通过流式事件回传前端
- 保存消息和相关记录

### 2. 为什么 `RAGChatServiceImpl` 是项目核心

- 因为它把聊天、检索、Prompt、模型调用、流式输出串起来了
- 这是系统最关键的业务编排点之一

### 3. Handler 起什么作用

- 负责统一处理聊天过程中的流式事件
- 把生成过程中的不同阶段转换成前端可消费的事件

### 4. 它和普通问答接口最大的区别

- 不是“拿问题直接问模型”
- 而是会结合上下文、检索、记忆、流式回传等复杂逻辑
