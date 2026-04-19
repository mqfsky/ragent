# Day 06 表结构、DO、Mapper 对应关系

今天把业务代码和数据库表真正连起来。

## 今天看哪些文件

- [resources/database/schema_pg.sql](/Users/mqf/project/ragent/resources/database/schema_pg.sql)
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/knowledge/dao/entity/`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/knowledge/dao/mapper/`

## 今天重点看什么

### 1. 先只找和昨天业务相关的表

重点找：

- `t_knowledge_base`
- `t_knowledge_document`
- `t_knowledge_chunk`

### 2. 看 DO 和表字段如何对应

今天要建立一个很重要的感觉：

- SQL 里的表字段
- Java 里的 DO 字段
- Mapper 操作

这三者之间是可以对应起来的。

### 3. 理解 Mapper 的角色

你可以先把 Mapper 理解成：

- 帮 Service 访问数据库的一层

## 今天的输出

请自己画一条简单链路：

接口请求 -> Service -> Mapper -> 数据表

并补充你今天识别出的 3 张表各自用途。

## 打卡检查

你今天应该能回答：

- 为什么读源码不能只看 Java 类，还要看表结构？
- `DO` 和 `VO` 的区别大概是什么？
- Service 为什么不应该直接写大量 SQL？

## 今天不要做的事

- 不要试图吃透整个 SQL 脚本
- 不要因为字段多就焦虑

你只需要做到“能对上号”。

## 参考答案提纲

### 1. 为什么读源码不能只看 Java 类，还要看表结构

- 因为很多业务最终都要落库
- 不看表结构，很难真正理解数据是怎么存的
- 一些业务限制和字段含义只在表设计里更清楚

### 2. `DO` 和 `VO` 的区别

- `DO`：更偏数据库对象，对应表结构
- `VO`：更偏接口返回对象，给前端展示

### 3. Service 为什么不应该直接写大量 SQL

- Service 更适合组织业务流程
- 数据访问职责应交给 Mapper 或 DAO
- 这样职责清晰，也便于维护

### 4. 这三张表大致用途

- `t_knowledge_base`：知识库基本信息
- `t_knowledge_document`：知识库下的文档信息
- `t_knowledge_chunk`：文档切分后的知识片段
