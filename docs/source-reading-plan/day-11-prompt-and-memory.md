# Day 11 Prompt 组装与会话记忆

今天看模型真正“看到”的内容是怎么拼出来的。

## 今天看哪些文件

- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/prompt/RAGPromptService.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/prompt/RAGPromptService.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/prompt/DefaultContextFormatter.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/prompt/DefaultContextFormatter.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/memory/JdbcConversationMemorySummaryService.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/memory/JdbcConversationMemorySummaryService.java)

## 今天重点看什么

### 1. Prompt 不是只放用户问题

今天你要明确：

- 用户问题
- 历史对话
- 检索结果
- MCP 结果

这些内容都可能一起组成最终 Prompt。

### 2. 为什么需要格式化上下文

模型不是直接读 Java 对象，所以系统要把检索结果整理成文本上下文。

### 3. 为什么需要会话摘要

历史消息太多会有两个问题：

- token 成本高
- 无关信息太多

所以系统需要摘要。

## 今天的输出

请自己回答：

1. 模型最终收到的输入包含哪些部分？
2. 为什么不能把所有历史消息原封不动都传给模型？

## 打卡检查

如果你已经能把“检索结果如何进入模型输入”讲清楚，今天就完成了。

## 今天不要做的事

- 不要纠结提示词文案本身写得优不优雅
- 不要研究所有格式化细节

先理解输入拼装机制。

## 参考答案提纲

### 1. 模型最终收到的输入包含哪些部分

- 用户当前问题
- 历史对话
- 检索出来的知识片段
- 可能还有 MCP 工具结果
- 系统提示词或场景提示

### 2. 为什么不能把所有历史消息都原封不动传给模型

- token 成本高
- 历史太长会降低有效信息密度
- 很多旧消息和当前问题无关

### 3. 为什么需要上下文格式化

- 因为模型读取的是文本，不是 Java 对象
- 所以系统需要把检索结果组织成结构清晰的文本证据

### 4. 会话摘要解决了什么问题

- 控制上下文长度
- 保留关键信息
- 提升多轮对话的稳定性和成本可控性
