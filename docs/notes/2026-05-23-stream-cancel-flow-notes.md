# 流式问答停止生成机制笔记

日期：2026-05-23

> 本文整理围绕“用户点击停止按钮后，Guava Cache + Redis 在取消流程中分别做什么”“后端实例、JVM、线程如何理解”“跨集群取消流程图和 StreamTaskInfo 生命周期图要注意什么”的讨论，便于后续源码复习和面试表达。

## 一句话理解

停止生成不是前端直接断开浏览器连接那么简单，而是要让真正执行大模型流式调用的后端实例停止模型 HTTP 流、保存已生成内容，并通过 SSE 通知前端收尾。

可以记成：

> Guava Cache 管“我这个 JVM 里正在跑什么、怎么停”；Redis 管“全局哪个 taskId 被要求停止，并把停止事件广播给所有后端实例”。

## 相关代码入口

- `frontend/src/components/chat/ChatInput.tsx`：生成中点击按钮会调用 `cancelGeneration()`。
- `frontend/src/stores/chatStore.ts`：拿到 `streamTaskId` 后调用 `stopTask(taskId)`。
- `frontend/src/services/chatService.ts`：请求 `POST /rag/v3/stop?taskId=xxx`。
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/controller/RAGChatController.java`：停止接口入口。
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/impl/RAGChatServiceImpl.java`：`stopTask` 委托给 `StreamTaskManager`。
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/handler/StreamTaskManager.java`：Guava Cache + Redis 协作取消的核心。
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/handler/StreamChatEventHandler.java`：注册任务、处理 SSE 事件、取消时落库。
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/pipeline/StreamChatPipeline.java`：绑定 LLM 流式取消句柄。
- `infra-ai/src/main/java/com/nageoffer/ai/ragent/infra/chat/StreamCancellationHandles.java`：最终通过 `call.cancel()` 中断模型 HTTP 流。

## Guava Cache 做什么

`StreamTaskManager` 内部有一张本地任务表：

```java
private final Cache<String, StreamTaskInfo> tasks = CacheBuilder.newBuilder()
        .expireAfterWrite(CANCEL_TTL)
        .maximumSize(10000)
        .build();
```

它的 key 是 `taskId`，value 是 `StreamTaskInfo`。

`StreamTaskInfo` 里保存的是当前 JVM 内才能使用的运行态对象：

- `cancelled`：本地取消状态，使用 `AtomicBoolean` 做 CAS，保证取消逻辑只执行一次。
- `handle`：大模型流式调用的取消句柄，最终会中断底层 HTTP 调用。
- `sender`：当前 SSE 连接的发送器，用于给前端发送 `CANCEL`、`DONE` 等事件。
- `onCancelSupplier`：取消时保存已生成内容，并返回 `messageId`、`title` 给前端。

注意：

> Guava Cache 不是每个线程一份，而是当前后端实例/JVM 内所有线程共享的一张本地任务表。

任务可能被多个线程接触：

- Web 请求线程处理 `/rag/v3/chat`。
- 模型流式线程读取大模型 token。
- Web 请求线程处理 `/rag/v3/stop`。
- Redis Topic 监听线程接收取消广播。

所以不能说“Guava Cache 记录每个线程自己的任务状态”。更准确是：

> Guava Cache 记录当前后端实例本地正在运行的流式任务状态。

## Redis 做什么

Redis 在这个取消流程中主要做两件事。

第一，保存取消标记：

```text
ragent:stream:cancel:{taskId} = true
```

这个 key 带 TTL，代码里是 30 分钟。它不是完整任务状态，也不是永久业务状态，只是临时取消事实。

第二，发布取消广播：

```java
topic.publish(taskId)
```

所有后端实例都订阅同一个取消 Topic。收到广播后，每个实例都会查自己的本地 Guava Cache：

- 如果本机有这个 `taskId`，说明任务在本机执行，执行 `cancelLocal(taskId)`。
- 如果本机没有这个 `taskId`，说明任务不在本机，直接跳过。

注意：

> Redis 不保存 `sender`、`handle`、线程信息，也不负责真正中断大模型。真正中断模型流的是持有本地 `StreamTaskInfo` 的后端实例。

## 后端实例、JVM、线程如何理解

可以按这个层级理解：

```text
服务器 / 容器 / 进程环境
  └── 后端实例
        └── JVM
              └── Java 应用代码
                    └── 线程
                          └── 某个任务的一段执行
```

在这个项目语境里，可以近似理解为：

```text
一个后端实例 ≈ 一个正在运行的 Spring Boot 应用 ≈ 一个 JVM 进程
```

JVM 是 Java 程序运行时所在的进程级运行环境，负责类加载、内存管理、GC、线程调度和字节码执行。

后端实例是部署视角下的一份应用副本。线上如果部署 3 个副本：

```text
ragent-app-1
ragent-app-2
ragent-app-3
```

那就是 3 个后端实例。每个实例通常都有自己的 JVM、内存、线程池和 Guava Cache。

线程只是 JVM 里执行代码的工作单元。线程不适合持有整个任务的跨阶段状态，因为一次流式问答会跨多个线程、多个回调和多个事件。

## 跨集群取消流程图如何理解

这张图的核心场景是：

- 节点 B 正在执行流式生成，和大模型保持连接，并通过 SSE 给前端推 token。
- 用户点击停止后，`POST /rag/v3/stop?taskId=xxx` 这次 HTTP 请求可能被负载均衡打到节点 A。
- 节点 A 自己没有这个任务，所以不能直接停止大模型。
- 节点 A 通过 Redis 写取消标记并发布广播。
- 节点 B 收到广播，发现本机 Guava Cache 有这个 `taskId`，于是执行真正的取消。

时序可以简化成：

```text
前端点击停止
  -> POST /rag/v3/stop?taskId=xxx
  -> 节点 A 写 Redis 取消标记
  -> 节点 A 发布 Redis Topic 广播
  -> 所有节点收到广播
  -> 节点 B 查到本地有任务
  -> CAS 标记 cancelled=true
  -> handle.cancel() 中断大模型流
  -> 保存已生成内容
  -> SSE 推送 CANCEL + DONE
  -> 前端关闭流并显示“已停止生成”
```

### 这张图要注意什么

1. 节点 A / 节点 B 不是固定角色。

节点 A 只是“刚好收到停止请求的实例”，节点 B 只是“刚好持有流式任务的实例”。真实线上可能是任意两个实例，也可能就是同一个实例。

2. Redis 不真正停止大模型。

Redis 只负责写取消标记和广播 taskId。真正调用 `handle.cancel()` 的是持有本地任务的后端实例。

3. `200 OK` 不等于前端已经完成取消收尾。

停止接口返回成功只表示取消请求已经被接收、标记和广播。前端真正收尾要等 SSE 收到：

```text
CANCEL {messageId, title}
DONE [DONE]
```

4. 停止不是瞬间零 token。

从点击停止到节点 B 真正取消模型流之间，可能已经有 token 在路上。所以前端可能先收到一些 `MESSAGE`，再收到 `CANCEL` 和 `DONE`。

5. Redis 取消标记是 TTL 临时状态。

图里如果写“持久化取消状态”，应理解成“带 TTL 的临时取消标记”，不是数据库中的永久任务状态。

## StreamTaskInfo 生命周期图如何理解

`StreamTaskInfo` 是本地流式任务的控制台。它从创建到结束大致经历下面几个阶段。

### 1. 创建任务条目

创建时通过：

```java
getOrCreate(taskId)
```

在 Guava Cache 中创建：

```text
taskId -> StreamTaskInfo
```

初始状态：

```text
cancelled = false
handle = null
sender = null
onCancelSupplier = null
```

### 2. 注册 SSE sender 和取消回调

`register(sender, supplier)` 会绑定：

- `sender`：用于推送 SSE 事件。
- `onCancelSupplier`：用于取消时保存已生成内容。

注册后会检查 Redis 是否已经存在取消标记。

如果 Redis 中已经有取消标记，说明可能发生了“停止请求先到，任务注册后到”的竞态。此时直接推送：

```text
CANCEL
DONE
```

并关闭 SSE 连接。

### 3. 绑定大模型取消句柄

后续调用大模型流式接口后，会执行：

```java
bindHandle(taskId, handle)
```

这里的 `handle` 才是真正能停止模型流的对象。

绑定时会再次检查本地 `cancelled` 标记。如果已经取消，则立即执行：

```java
handle.cancel()
```

这解决的是另一个竞态：

```text
取消广播已经到了
本地 cancelled 已经是 true
但 LLM handle 现在才绑定上来
```

### 4. 流式生成中

生成过程中，`onContent` 和 `onThinking` 每次回调都会检查：

```java
taskManager.isCancelled(taskId)
```

如果已经取消，就不再继续向前端发送内容。

### 5. 正常完成路径

正常完成时：

```text
onComplete()
  -> 完整内容落库
  -> 推送 FINISH + DONE
  -> sender.complete()
  -> unregister(taskId)
```

`unregister(taskId)` 会主动清理：

```text
Cache.invalidate(taskId)
Redis cancel key deleteAsync
```

### 6. 取消路径

用户停止时：

```text
cancelLocal(taskId)
  -> CAS 设置 cancelled=true
  -> handle.cancel()
  -> 保存已生成内容
  -> 推送 CANCEL + DONE
  -> sender.complete()
```

取消路径当前不会立刻 `unregister`，而是让本地 Cache 条目和 Redis cancel key 等待 TTL 兜底过期。

## 为什么需要两次取消检查

注册 sender 后查 Redis，是为了处理：

```text
停止请求先到
任务还没来得及注册 sender / supplier
```

绑定 handle 后查本地 `cancelled`，是为了处理：

```text
取消广播先到
handle 还没创建或还没绑定
```

这两次检查分别兜住了不同阶段的并发时序。

面试可以说：

> 流式任务启动不是一个原子动作，sender 注册、LLM handle 创建、模型流开始读取之间存在时间窗口。Redis 取消标记和本地 cancelled 状态分别兜住“先取消后注册”和“先取消后绑定 handle”的竞态，保证停止请求不会丢。

## CAS 的作用

取消流程里使用：

```java
cancelled.compareAndSet(false, true)
```

作用是保证取消收尾只执行一次。

可能重复触发取消的来源包括：

- 用户重复点击停止按钮。
- Redis 广播重复到达。
- 本地和远端取消路径并发。
- 超时、错误、取消同时发生。

CAS 成功的第一次执行真正取消，后续发现已经是 `true` 就直接返回，避免重复落库、重复发送 SSE、重复关闭连接。

## 前端事件顺序

正常完成：

```text
MESSAGE x N -> FINISH -> DONE
```

用户取消：

```text
MESSAGE x N -> CANCEL -> DONE
```

`CANCEL` 的作用是告诉前端“这是被用户停止的回答”，并携带可能已经落库的 `messageId` 和 `title`。

`DONE` 的作用是告诉前端“这个 SSE 流已经结束，可以关闭连接和清理 loading 状态”。

## 容易答错的点

错误说法：

> Guava Cache 会记录每个线程自己的任务状态，Redis 也会记录每个线程每个任务的状态。

更准确说法：

> Guava Cache 记录每个后端实例/JVM 本地正在运行的流式任务状态；Redis 记录全局可见的 taskId 取消标记，并负责通过 Pub/Sub 广播取消事件。

错误说法：

> Redis 负责取消大模型。

更准确说法：

> Redis 只负责协调和通知。真正取消大模型流的是持有任务的后端实例，通过本地 `StreamCancellationHandle` 调用底层 HTTP `call.cancel()`。

错误说法：

> 停止接口返回 200 就表示前端已经停止。

更准确说法：

> 200 只表示停止请求处理成功。前端最终停止要等 SSE 收到 `CANCEL` 和 `DONE`。

错误说法：

> 取消状态会永久存在 Redis。

更准确说法：

> Redis 中的取消标记是带 TTL 的临时标记，用来做跨实例协调和竞态兜底，不是永久业务状态。

## 面试表达模板

可以这样回答：

> 用户点击停止后，前端会携带当前流式任务的 taskId 调用 `/rag/v3/stop`。由于服务可能是多实例部署，停止请求不一定会打到真正执行流式生成的那台机器，所以后端不会只依赖本地内存。`StreamTaskManager` 会先在 Redis 写入一个带 TTL 的取消标记，然后通过 Redis Topic 广播 taskId。所有实例收到广播后都会检查自己的 Guava Cache，只有本地持有这个 taskId 的实例才会执行真正取消。

> Guava Cache 存的是当前 JVM 内的运行态对象，包括 SSE sender、LLM 取消句柄、取消回调和本地 cancelled 状态，这些对象不能放到 Redis。Redis 存的是全局可见的取消事实和广播事件。真正取消时，本地实例通过 CAS 把 cancelled 从 false 改成 true，保证取消逻辑只执行一次，然后调用 handle.cancel() 中断模型 HTTP 流，保存已生成的部分回答，最后通过 SSE 发送 CANCEL 和 DONE，让前端完成收尾。

## 最终心智模型

- 后端实例：一份正在运行的 ragent 后端应用。
- JVM：这份 Java 应用运行的进程级运行时环境。
- 线程：JVM 里执行代码的工作单元。
- Guava Cache：当前 JVM 内所有线程共享的本地任务登记表。
- Redis：多个后端实例共享的外部协调中心。
- Redis Bucket：保存 `taskId` 的临时取消标记。
- Redis Topic：把取消事件广播给所有实例。
- CAS：保证取消收尾只执行一次。
- `handle.cancel()`：真正中断大模型流式 HTTP 调用。
- `CANCEL + DONE`：前端取消收尾信号。
