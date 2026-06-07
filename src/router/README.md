# Router Directory (路由目录)

> 一旦我所属的文件夹有所变化，请更新我。

## 功能作用

配置 Vue Router 路由，定义页面路径和导航规则。

## 文件清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `index.js` | Router Config | 路由配置主文件 |
| `sessionNavigation.js` | Navigation Bridge | 为认证 store 提供登录跳转桥接，避免 store 动态导入 router |

## 路由结构

```javascript
const routes = [
  { path: '/login', component: Login },
  { path: '/dashboard', component: Dashboard, meta: { roles: ['admin', 'manager', 'staff'] } },
  // ...
]
```

## 导航守备

- **权限守备**: 检查 `meta.roles` 验证用户权限
- **登录状态**: 未登录用户重定向到登录页
