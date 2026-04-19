# Day 03 登录链路入门

今天开始正式读业务代码。目标是看懂一个最常见的后端链路：登录。

## 今天看哪些文件

- [bootstrap/src/main/java/com/nageoffer/ai/ragent/user/controller/AuthController.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/user/controller/AuthController.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/user/service/impl/AuthServiceImpl.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/user/service/impl/AuthServiceImpl.java)

## 今天重点看什么

### 1. Controller 负责什么

关注：

- 接口路径
- 请求参数
- 返回值
- Controller 有没有写复杂逻辑

正常情况下，Controller 应该只是接请求、调 Service、返回结果。

### 2. Service 负责什么

看 `AuthServiceImpl` 时重点找：

- 用户校验逻辑
- 登录成功后做了什么
- token 是怎么生成或返回的

### 3. 一条链路怎么走

你今天要尝试顺着这条线读：

请求 -> `AuthController` -> `AuthServiceImpl` -> 登录态处理 -> 返回前端

## 今天的输出

请自己写出：

1. 登录接口的 URL 是什么
2. 登录 Service 里做了哪几步事
3. Controller 和 Service 的职责边界是什么

## 打卡检查

你今天应该能回答：

- 为什么业务逻辑一般不直接写在 Controller 里？
- 登录成功后，前端后续请求靠什么识别用户？

## 今天不要做的事

- 不要一开始就查完整用户表结构
- 不要纠结工具类和异常类细节

先把主干链路读顺。

## 参考答案提纲

### 1. 登录接口的 URL 是什么

- 需要你从 `AuthController` 中确认具体映射
- 重点不是死记路径，而是知道它属于认证入口

### 2. 登录 Service 里做了哪几步事

- 接收登录参数
- 查询用户或校验用户信息
- 判断用户名密码是否正确
- 登录成功后写入登录态
- 返回 token 和用户基础信息给前端

### 3. Controller 和 Service 的职责边界

- `Controller`：接收 HTTP 请求、参数绑定、调用 Service、返回结果
- `Service`：承载核心业务逻辑和流程控制

### 4. 为什么业务逻辑不应该主要写在 Controller

- Controller 负责接口层，不适合堆业务细节
- 业务逻辑写在 Service 更方便复用、测试和维护

### 5. 登录成功后前端靠什么识别用户

- 依赖 token
- 后续请求携带 token，后端据此识别当前登录用户
