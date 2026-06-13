# 企业资质核心领域模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责说明

`businessqualification` 模块负责企业资质（Qualification）的底层领域模型、核心业务规则、状态流转以及数据持久化。
它通过纯核心域（domain）以及应用服务（application）来实现对资质证书的管理，包括创建、更新、列表查询、导入和到期提醒等。

## 目录结构

```
businessqualification
├── config/             # Spring 基础配置，用于装配领域策略与 Repository
├── domain/             # 纯核心领域逻辑包（受 FP-Java 架构规则约束）
│   ├── model/          # 资质实体核心模型 (BusinessQualification)
│   ├── valueobject/    # 值对象 (如资质状态、类型等)
│   ├── service/        # 领域服务 (如资质校验服务)
│   └── port/           # 输出端口，用于与基础设施解耦
├── application/        # 应用编排层
│   ├── service/        # 应用服务，处理用例流转及事务
│   ├── command/        # 读写命令/参数封装
│   └── view/           # 视图层或展现层相关数据结构
└── infrastructure/     # 基础设施层
    └── persistence/    # JPA 数据访问适配器实现
```

## 边界清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `config/QualificationDomainConfig.java` | 配置层 | 统一注册并装配领域相关 Bean，实现配置与实体分离 |
| `domain/model/BusinessQualification.java` | 纯核心 | 资质核心实体，提供不可变状态转换与规则判定 |
| `domain/port/BusinessQualificationRepository.java` | 端口 | 资质持久化输出端口 |
| `application/service/CreateQualificationAppService.java` | 应用服务 | 资质创建应用逻辑编排 |
| `application/service/UpdateQualificationAppService.java` | 应用服务 | 资质更新应用逻辑编排 |
| `application/service/DeleteQualificationAppService.java` | 应用服务 | 资质删除应用逻辑编排 |
| `application/service/ListQualificationsAppService.java` | 应用服务 | 资质查询与列表过滤编排 |
| `application/service/ImportQualificationAppService.java` | 应用服务 | 从 Excel 模板导入资质数据编排 |
| `application/service/AlertConfigAppService.java` | 应用服务 | 资质过期预警天数配置管理 |
| `infrastructure/persistence/BusinessQualificationRepositoryAdapter.java` | 适配器 | JPA 仓储适配器，实现 `BusinessQualificationRepository` 接口 |
