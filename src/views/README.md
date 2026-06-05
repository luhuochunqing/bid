# Views Directory (页面目录)

> 一旦我所属的文件夹有所变化，请更新我。

## 功能作用

存放所有页面级组件，按功能模块组织，对应路由中的各个页面。

## 文件清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `Login.vue` | View | 登录页 |
| `Dashboard/` | 目录 | 工作台页面 |
| `Bidding/` | 目录 | 标讯中心页面 |
| `Project/` | 目录 | 投标项目页面 |
| `Task/` | 目录 | 任务管理页面 |
| `Knowledge/` | 目录 | 知识资产页面 |
| `Resource/` | 目录 | 资源管理页面 |
| `Analytics/` | 目录 | 数据分析页面 |
| `AI/` | 目录 | AI 智能中心页面 |
| `Document/` | 目录 | 文档管理页面 |
| `System/` | 目录 | 系统设置页面 |

## 路由对应

| 路径 | 组件 |
|------|------|
| `/login` | `Login.vue` |
| `/dashboard` | `Dashboard/` |
| `/bidding` | `Bidding/` |
| `/project` | `Project/` |
| `/task` | `Task/` |
| `/knowledge/*` | `Knowledge/` |
| `/resource/*` | `Resource/` |
| `/analytics/*` | `Analytics/` |
| `/ai-center` | `AI/` |

## 权限控制

通过 `meta.roles` 配置页面访问权限：
- `admin`: 管理员
- `manager`: 经理
- `staff`: 普通员工
