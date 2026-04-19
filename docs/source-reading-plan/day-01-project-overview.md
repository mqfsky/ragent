# Day 01 项目总览与模块认知

今天的目标只有一个：先知道这个项目“是什么”，不要急着看实现细节。

## 今天看哪些文件

- [README.md](/Users/mqf/project/ragent/README.md)
- [pom.xml](/Users/mqf/project/ragent/pom.xml)

## 今天重点看什么

### 1. 项目是干什么的

从 `README.md` 中先理解：

- 它是一个 `RAG + Agent + MCP` 项目
- 它不只是聊天，还包含知识库、文档入库、工具调用、后台管理
- 它是前后端分离项目

### 2. 项目有哪些模块

从 `pom.xml` 中重点关注四个模块：

- `bootstrap`
- `framework`
- `infra-ai`
- `mcp-server`

你今天不用记住每个模块的所有细节，只要知道：

- `bootstrap` 是主业务
- `framework` 是公共基础设施
- `infra-ai` 是模型调用层
- `mcp-server` 是工具服务

## 今天的输出

看完后，请你自己写出下面这 3 句话：

1. 这个项目解决的核心问题是什么？
2. 这个项目和普通 Spring Boot CRUD 项目有什么区别？
3. 四个 Maven 模块分别负责什么？

## 打卡检查

如果你能回答下面的问题，今天就算完成：

- 什么是 RAG？
- 这个项目为什么不只是一个聊天接口？
- `bootstrap` 和 `framework` 的区别是什么？

## 今天不要做的事

- 不要一上来就读 `service impl`
- 不要试图读完整个 README
- 不要纠结向量数据库原理

今天只做全景认知。

## 参考答案提纲

### 1. 这个项目解决的核心问题是什么

- 这是一个面向企业知识问答场景的 RAG 系统
- 它把知识库、文档入库、检索增强、模型问答、工具调用整合在一起
- 它的目标不是单纯聊天，而是让模型基于企业私有知识和工具能力回答问题

### 2. 这个项目和普通 Spring Boot CRUD 项目有什么区别

- 普通 CRUD 项目重点是增删改查
- 这个项目除了后台管理，还包含检索、向量存储、流式输出、模型调用、工具调用
- 它依赖更多基础设施，例如 Redis、RocketMQ、向量检索和模型服务

### 3. 四个 Maven 模块分别负责什么

- `bootstrap`：主业务模块，承载聊天、知识库、入库流水线、后台接口
- `framework`：公共基础设施模块，提供 Web、Redis、MQ、幂等、通用工具能力
- `infra-ai`：模型基础设施模块，封装 chat、embedding、rerank 及路由能力
- `mcp-server`：MCP 工具服务模块，对外暴露可被主系统调用的工具
