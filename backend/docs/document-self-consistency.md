# 文档自洽规则 (Document Self-Consistency Rules)

> 一旦我所属的文件夹有所变化，请更新我。

## 概述

本文档定义了项目文档自洽性规范，确保文档、源码注释与实际目录结构保持高度同步。

## 目录文档规范

### 白名单目录（无需 README.md）

| 类型 | 目录 | 原因 |
|------|------|------|
| 依赖产物 | `node_modules/`, `target/`, `dist/`, `build/` | 第三方库/编译产物 |
| 自动生成 | `.worktrees/`, `.git/` | Git/工具生成 |
| 缓存 | `.cache/`, `coverage/` | 运行时缓存 |
| 静态资源 | `src/assets/` | 静态资源文件 |

### README.md 必备内容

```markdown
# [模块名称]

一旦我所属的文件夹有所变化，请更新我。

## 功能作用
[1-3行描述目录功能]

## 文件清单

| 文件 | 地位 | 功能 |
|------|------|------|
| FileName.java | Entity | xxx实体 |
| FileName.java | Service | xxx业务逻辑 |
| ... | ... | ... |
```

### 文件地位分类

**后端 (Java)**:
- `Entity` - JPA实体类
- `DTO` - 数据传输对象
- `Repository` - 数据访问层
- `Service` - 业务逻辑层
- `Controller` - REST API控制器
- `Config` - 配置类
- `Exception` - 异常类
- `Util` - 工具类

**前端 (Vue/JS)**:
- `View` - 页面组件
- `Component` - 可复用组件
- `Store` - Pinia状态
- `API` - API调用
- `Router` - 路由配置
- `Util` - 工具函数
- `Style` - 样式文件

## 源码头注释规范

### Java 类模板

```java
// Input: [依赖项/注入的服务]
// Output: [产出/角色]
// Pos: [位置/层次架构]
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.xxx;

import ...
```

### Java 方法模板

```java
// Input: [参数类型和说明]
// Output: [返回值类型和含义]
// Pos: [在业务流程中的位置]
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
public ResultType methodName(ParamType param) {
    // ...
}
```

### Vue 组件模板

```vue
<!--
Input: [Props依赖]
Output: [组件产出/事件]
Pos: [组件层次]
一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
-->
<script setup>
// ...
</script>
```

## 更新流程

### 开发前
1. 阅读目标目录的 README.md
2. 理解模块架构和文件职责

### 开发中
1. 新增文件时，添加头注释
2. 修改文件时，更新头注释

### 开发后
1. 更新受影响目录的 README.md 文件清单
2. 运行 `npm run check-docs` 或 `./scripts/check-doc-consistency.sh` 验证
3. 提交前确保检查通过

## 自动化检查

### 运行检查

```bash
# 前端项目
npm run check-docs

# 后端项目
cd backend && ./scripts/check-doc-consistency.sh
```

### 检查内容

- [ ] 非白名单目录是否有 README.md
- [ ] README.md 是否包含更新声明
- [ ] README.md 文件清单是否完整
- [ ] 关键业务源码是否有头注释

## 示例

### Entity 类示例

```java
// Input: JPA Persistence, Spring Data
// Output: Fee实体 - 费用数据持久化模型
// Pos: 数据层 - 费用管理模块
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.fees.entity;

@Entity
@Table(name = "fees")
public class Fee {
    // ...
}
```

### Service 类示例

```java
// Input: FeeRepository, AuditLogService
// Output: 费用业务逻辑处理
// Pos: 业务层 - 费用管理模块
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
@Service
public class FeeService {

    // Input: projectId (项目ID), status (费用状态)
    // Output: 分页费用数据
    // Pos: 费用查询业务逻辑
    // 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
    public Page<FeeDTO> getFeesByProjectAndStatus(Long projectId, FeeStatus status) {
        // ...
    }
}
```

## 版本历史

| 日期 | 版本 | 变更 |
|------|------|------|
| 2026-03-11 | 1.0 | 初始版本 |
