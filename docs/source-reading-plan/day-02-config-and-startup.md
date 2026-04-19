# Day 02 配置入口与启动路径

今天的目标是知道：项目启动后依赖了哪些外部组件，Spring Boot 从哪里开始工作。

## 今天看哪些文件

- [bootstrap/src/main/resources/application.yaml](/Users/mqf/project/ragent/bootstrap/src/main/resources/application.yaml)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/RagentApplication.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/RagentApplication.java)

## 今天重点看什么

### 1. 应用端口和访问前缀

看：

- `server.port`
- `server.servlet.context-path`

你要知道后端接口实际挂在哪个路径下。

### 2. 项目依赖了哪些外部组件

重点识别这些配置项：

- `spring.datasource`
- `spring.data.redis`
- `rocketmq`
- `milvus`
- `rag.mcp.servers`
- `ai.providers`
- `rustfs`

今天不用记住所有字段含义，但要知道这些组件分别是：

- 数据库
- 缓存
- 消息队列
- 向量存储
- MCP 工具服务
- 模型服务
- 文件存储

### 3. 启动类的作用

看 `RagentApplication` 时，只要理解：

- Spring Boot 从这里启动
- 启动后会扫描 Bean、加载配置、初始化整个系统

## 今天的输出

请自己总结一张小表：

- 组件名
- 在项目中的作用
- 配置入口在哪

## 打卡检查

你今天应该能回答：

- 项目的 HTTP 端口是多少？
- 聊天接口为什么不是单独一个简单接口，而依赖很多外部组件？
- 当前默认向量方案是 `pgvector` 还是 `Milvus`？

## 今天不要做的事

- 不要深挖每个配置属性
- 不要研究具体模型参数

今天只建立“系统依赖地图”。

## 参考答案提纲

### 1. 项目的 HTTP 端口是多少

- 从 `application.yaml` 看，后端端口是 `9090`
- 上下文路径是 `/api/ragent`

### 2. 聊天接口为什么依赖很多外部组件

- 聊天不是只调一次模型
- 它需要数据库保存会话与消息
- 需要 Redis 做缓存、锁和并发控制
- 需要向量存储做知识检索
- 需要模型服务提供 chat、embedding、rerank
- 有些链路还依赖 RocketMQ、MCP Server 和对象存储

### 3. 当前默认向量方案是什么

- 当前默认是 `pgvector`
- 证据是 `rag.vector.type: pg`
- `Milvus` 仍然保留为可选实现

### 4. 外部组件大致作用

- PostgreSQL：业务主库
- Redis：缓存与并发控制
- RocketMQ：异步消息
- pgvector 或 Milvus：向量检索
- RustFS/S3：文件存储
- Ollama/百炼/SiliconFlow：模型服务
- MCP Server：工具服务
