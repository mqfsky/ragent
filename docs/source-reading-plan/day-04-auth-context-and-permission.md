# Day 04 登录态、权限与用户上下文

今天把昨天的登录链路补完整，理解“登录之后系统怎么知道你是谁”。

## 今天看哪些文件

- [bootstrap/src/main/java/com/nageoffer/ai/ragent/user/controller/UserController.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/user/controller/UserController.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/user/config/SaTokenConfig.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/user/config/SaTokenConfig.java)
- [bootstrap/src/main/java/com/nageoffer/ai/ragent/user/config/UserContextInterceptor.java](/Users/mqf/project/ragent/bootstrap/src/main/java/com/nageoffer/ai/ragent/user/config/UserContextInterceptor.java)

## 今天重点看什么

### 1. 权限校验是怎么做的

重点找：

- 哪些接口要求登录
- 哪些接口要求管理员角色
- 是在 Controller 里校验，还是拦截器/配置里统一处理

### 2. 当前用户信息是怎么拿到的

你要重点理解：

- token 在请求到来时如何被解析
- 当前登录用户信息如何传入后续业务

### 3. 什么是拦截器

你可以先把拦截器理解成：

- 请求到达 Controller 前先经过的一层统一处理

## 今天的输出

请自己回答：

1. 普通用户和管理员接口是如何区分的？
2. 为什么很多业务代码不需要手动传 userId？
3. `UserContextInterceptor` 做了什么？

## 打卡检查

如果你能讲清楚“请求是如何带着登录身份进入业务代码”的，今天就完成了。

## 今天不要做的事

- 不要被配置类里的所有细节吓住
- 不要纠结框架注解的底层源码

今天的核心是理解机制，不是研究框架底层。

## 参考答案提纲

### 1. 普通用户和管理员接口如何区分

- 通过登录态和角色校验区分
- 某些接口会显式要求 `admin` 角色
- 有的鉴权逻辑在 Controller 中调用校验方法，有的可能通过统一配置处理

### 2. 为什么很多业务代码不需要手动传 userId

- 因为请求进入系统时，登录态已经能识别当前用户
- 拦截器或上下文组件会把当前用户信息放到线程上下文中
- 后续业务代码可以直接从上下文读取当前用户

### 3. `UserContextInterceptor` 做了什么

- 在请求进入 Controller 前执行
- 读取当前登录用户身份
- 把用户信息写入上下文，方便后续业务使用

### 4. `Sa-Token` 在这里承担什么角色

- 管理登录态
- 校验是否登录
- 校验角色或权限
