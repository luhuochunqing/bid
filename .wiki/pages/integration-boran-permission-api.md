---
title: 西域给泊冉权限接口
space: engineering
category: integration
tags: [integration, boran, permission, 角色, 权限, xiyu-to-boran]
sources:
  - docs/references/xiyu-to-boran-permission-api-23353-login.md
  - docs/references/xiyu-to-boran-permission-api-23358-getUserInfo.md
  - docs/references/xiyu-to-boran-permission-api-23484-getUserPermission.md
  - docs/references/xiyu-to-boran-permission-api-26325-getUserJobListByJobNumberList.md
  - docs/references/xiyu-to-boran-permission-api-23370-logout.md
backlinks:
  - _index
  - roles-and-permissions
created: 2026-06-22
updated: 2026-06-27
health_checked: 2026-06-27
---
# 西域给泊冉权限接口

> 暗号：**角色和权限**
> 来源：西域（OSS）提供给泊冉的 5 个标准权限接口
> 更新于：2026-06-23

本页汇总西域给泊冉的 5 个权限接口文档，用于用户认证、员工信息获取、菜单权限、角色查询与登出。

---

## 接口调用顺序

| 顺序 | 接口名称 | 接口路径 | YApi 编号 |
| --- | --- | --- | --- |
| 1 | 登录接口 | `POST /oauth/login` | [23353](https://yapi.ehsy.com/project/406/interface/api/23353) |
| 2 | 根据 token 获取员工信息接口 | `GET /oauth/getUserInfo` | [23358](https://yapi.ehsy.com/project/406/interface/api/23358) |
| 3 | 根据 token 获取用户系统权限接口 | `GET /oauth/getUserPermission` | [23484](https://yapi.ehsy.com/project/406/interface/api/23484) |
| 4 | 根据工号列表查询用户角色 | `POST /oss/admin-web/v1/output/data/getUserJobListByJobNumberList` | [26325](https://yapi.ehsy.com/project/406/interface/api/26325) |
| 5 | 登出接口 | `POST /oauth/logout` | [23370](https://yapi.ehsy.com/project/406/interface/api/23370) |

> 接口 2 和接口 3 不区分先后顺序。

---

## 接口文档清单

| 文档 | 路径 | 说明 |
| --- | --- | --- |
| 登录接口 | [docs/references/xiyu-to-boran-permission-api-23353-login.md](../../docs/references/xiyu-to-boran-permission-api-23353-login.md) | 获取 access_token / refresh_token |
| 根据 token 获取员工信息 | [docs/references/xiyu-to-boran-permission-api-23358-getUserInfo.md](../../docs/references/xiyu-to-boran-permission-api-23358-getUserInfo.md) | 根据 token 获取员工基本信息 |
| 根据 token 获取用户系统权限 | [docs/references/xiyu-to-boran-permission-api-23484-getUserPermission.md](../../docs/references/xiyu-to-boran-permission-api-23484-getUserPermission.md) | 获取用户在各系统的菜单权限 |
| 根据工号列表查询用户角色 | [docs/references/xiyu-to-boran-permission-api-26325-getUserJobListByJobNumberList.md](../../docs/references/xiyu-to-boran-permission-api-26325-getUserJobListByJobNumberList.md) | 批量查询用户角色与岗位信息 |
| 登出接口 | [docs/references/xiyu-to-boran-permission-api-23370-logout.md](../../docs/references/xiyu-to-boran-permission-api-23370-logout.md) | 用户登出并记录日志 |

---

## 缓存策略

菜单权限或用户角色在登录时写入缓存，退出登录时删除缓存，重新登录后获取最新权限数据。

---

## 相关链接

- [角色与权限](roles-and-permissions.md)
- [集成 - OA/CRM](integration-oa-crm.md)
- [西域给泊冉权限接口开发计划](../../docs/references/xiyu-to-boran-permission-api-development-plan.md)
- [西域给泊冉权限接口实施计划](../../docs/references/xiyu-to-boran-permission-api-implementation-plan.md)
- [西域给泊冉权限接口差距分析](../../docs/references/xiyu-to-boran-permission-api-gap-analysis.md)
