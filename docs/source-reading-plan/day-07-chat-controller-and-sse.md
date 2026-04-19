# Day 07 聊天接口入口与 SSE

今天开始进入项目最核心的主链路：聊天。

## 今天看哪些文件

- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/controller/RAGChatController.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/controller/RAGChatController.java)
- [framework/src/main/java/com/nageoffer/ai/ragent/framework/web/SseEmitterSender.java](/Users/mqf/project/ragent/framework/src/main/java/com/nageoffer/ai/ragent/framework/web/SseEmitterSender.java)

## 今天重点看什么

### 1. 聊天接口长什么样

先观察：

- 路径是什么
- 参数有哪些
- 返回值为什么不是普通 JSON，而是 `SseEmitter`

### 2. 什么是 SSE

今天不用学网络协议细节。先记住：

- SSE 是服务端持续向前端推送数据的一种方式
- 适合聊天这种“边生成边显示”的场景

### 3. 流式输出为什么重要

如果后端必须等模型全部生成完再返回，用户体验会差很多。

所以你今天要理解：

- 为什么聊天接口需要流式返回
- `SseEmitterSender` 在其中承担了什么角色

## 今天的输出

请自己写出：

1. 聊天接口为什么不适合普通同步返回？
2. `SseEmitter` 解决了什么问题？
3. `Controller` 在聊天链路里负责到哪一步？

## 打卡检查

如果你已经能接受“聊天请求是一个持续输出过程，而不是一次性返回结果”，今天就算达标。

## 今天不要做的事

- 不要今天就追完整个聊天 Service
- 不要纠结前端 EventSource 细节

先把“为什么要 SSE”建立起来。

## 参考答案提纲

### 1. 聊天接口为什么不适合普通同步返回

- 因为模型生成答案通常需要时间
- 如果等全部生成完再返回，用户等待感会很强
- 流式返回可以边生成边展示

### 2. `SseEmitter` 解决了什么问题

- 让后端能持续向前端推送事件
- 非常适合聊天场景的增量输出

### 3. `Controller` 在聊天链路里负责到哪一步

- 接收请求参数
- 创建或传入 `SseEmitter`
- 调用聊天 Service
- 把具体业务处理交给下层

### 4. 为什么聊天场景常用流式输出

- 更快给用户首字反馈
- 更符合大模型逐步生成答案的方式
