# API 集成指南

## 概述

项目运行**唯一走真实后端 API**（API-only）。`src/api/mock.js` 与 `src/api/mock-adapters/` 已彻底移除，`config.js` 硬编码 `mode: 'api'`，不再读取 `VITE_API_MODE`。本地启动用 `npm run dev:all` 或 `backend/start.sh` 即可连通后端。

> **本指南聚焦"前后端联调"**。如果你是外部集成方（OA / CRM / 企业微信等）想要对接接口，请直接看下方 OpenAPI 入口。

## OpenAPI 接口规范（外部集成方入口）

后端集成 `springdoc-openapi` 2.3.0，自动生成机器可读的 OpenAPI 3.0 规范与可视化 Swagger UI 调试门户：

| 入口 | URL（本地） | 说明 |
|---|---|---|
| Swagger UI 门户 | http://127.0.0.1:18080/swagger-ui.html | 浏览所有接口、在线调试（支持 JWT Authorize） |
| OpenAPI JSON | http://127.0.0.1:18080/v3/api-docs | 给集成方导入 Postman / Apifox / 自动生成 SDK |
| OpenAPI YAML | http://127.0.0.1:18080/v3/api-docs.yaml | 同上，YAML 格式 |

调试受保护接口的步骤：
1. `POST /api/auth/login` 拿到 JWT token
2. Swagger UI 右上角点 **Authorize**，输入 `Bearer <token>`
3. 任意点击端点的 **Try it out** 即可调试

详细背景、配置、限制与后续规划见 [.wiki/pages/api-openapi.md](../.wiki/pages/api-openapi.md)。

组织架构集成仍遵循真实 API 单一路径：YAPI 契约映射见 [organization-directory-yapi-mapping.md](../integration/organization-directory-yapi-mapping.md)，部署与验收 Runbook 见 [organization-directory-runbook.md](../integration/organization-directory-runbook.md)。OA 流程创建和 CRM 客户接口不属于该组织架构切片。

## 目录结构

```
src/api/
├── config.js              # API 配置（硬编码 mode='api' + 基础URL）
├── client.js              # Axios 客户端封装
├── trendradar.js          # TrendRadar API（已存在）
├── modules/               # 按模块分组的 API 调用
│   ├── auth.js           # 认证模块
│   ├── tenders.js        # 标讯模块
│   ├── projects.js       # 项目模块
│   ├── knowledge.js      # 知识库模块
│   ├── fees.js           # 费用模块
│   ├── ai.js             # AI 分析模块
│   ├── resources.js      # 资源管理模块
│   ├── collaboration.js  # 协作与文档模块
│   └── dashboard.js      # 数据看板与任务模块
└── index.js              # 统一导出
```

## 启动方式

系统默认就是真实 API 模式。启动前后端联调请用根目录的 `npm run dev:all`（同时拉起前后端），或单独运行 `backend/start.sh` + `npm run dev`。详见仓库根目录 `CLAUDE.md` 的"推荐命令"章节。

> 部分脚本（`scripts/dev-*`、`scripts/release/*`）仍会显式注入 `VITE_API_MODE=api` 环境变量 —— 这是为了配合 `scripts/dev-frontend-health.sh` 的健康检查，不代表 `config.js` 还支持切换。

## 使用示例

### 1. 在组件中使用

```vue
<script setup>
import { ref, onMounted } from 'vue'
import { authApi, projectsApi, tendersApi } from '@/api'

const user = ref(null)
const projects = ref([])

// 登录
const handleLogin = async () => {
  const result = await authApi.login('小王', '123456')
  if (result.success) {
    user.value = result.data.user
    localStorage.setItem('token', result.data.token)
  }
}

// 获取项目列表
const loadProjects = async () => {
  const result = await projectsApi.getList({ status: 'bidding' })
  if (result.success) {
    projects.value = result.data
  }
}

onMounted(() => {
  handleLogin()
  loadProjects()
})
</script>
```

### 2. 在 Pinia Store 中使用

```javascript
// src/stores/user.js
import { defineStore } from 'pinia'
import { authApi } from '@/api'

export const useUserStore = defineStore('user', {
  state: () => ({
    user: null,
    token: null
  }),

  actions: {
    async login(username, password) {
      const result = await authApi.login(username, password)
      if (result.success) {
        this.user = result.data.user
        this.token = result.data.token
        localStorage.setItem('token', this.token)
        localStorage.setItem('user', JSON.stringify(this.user))
        return true
      }
      return false
    },

    logout() {
      this.user = null
      this.token = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }
})
```

### 3. 各模块 API 调用示例

```javascript
import {
  authApi,
  tendersApi,
  projectsApi,
  knowledgeApi,
  feesApi,
  aiApi,
  resourcesApi,
  collaborationApi,
  dashboardApi
} from '@/api'

// 认证
await authApi.login('username', 'password')
await authApi.getCurrentUser()
await authApi.logout()

// 标讯
await tendersApi.getList({ status: 'new' })
await tendersApi.getDetail('B001')
await tendersApi.getAIAnalysis('B001')

// 项目
await projectsApi.getList()
await projectsApi.getDetail('P001')
await projectsApi.create({ name: '新项目', customer: '客户A' })
await projectsApi.getTasks('P001')

// 知识库
await knowledgeApi.qualifications.getList()
await knowledgeApi.cases.getList({ industry: '政府' })
await knowledgeApi.templates.getList({ category: '技术方案' })

// 费用
await feesApi.getList({ status: 'pending' })
await feesApi.pay('F001', { method: 'bank' })
await feesApi.getStatistics()

// AI 分析
await aiApi.score.getAnalysis('P001')
await aiApi.competition.getProjectAnalysis('P001')
await aiApi.roi.getAnalysis('P001')
await aiApi.compliance.getCheckResult('P001')

// 资源
await resourcesApi.accounts.getList()
await resourcesApi.barSites.getList()
await resourcesApi.certificates.borrow('UK001', '小王', '某项目')

// 协作
await collaborationApi.calendar.getMonthEvents(2026, 3)
await collaborationApi.collaboration.getThreads()
await collaborationApi.versions.getVersions('DOC001')

// 看板
await dashboardApi.dashboard.getStats()
await dashboardApi.tasks.getList({ status: 'pending' })
```

## API 响应格式

### 成功响应

```javascript
{
  success: true,
  data: { ... },
  message: "操作成功"
}
```

### 错误响应

```javascript
{
  success: false,
  message: "错误信息"
}
```

## 拦截器说明

### 请求拦截器

自动添加 Token：
```javascript
config.headers.Authorization = `Bearer ${token}`
```

### 响应拦截器

- 401：自动跳转登录页
- 403：显示权限错误
- 404：显示资源不存在
- 500：显示服务器错误
- 网络错误：显示连接失败提示

## 错误处理

```javascript
import { authApi } from '@/api'

try {
  const result = await authApi.login('user', 'pass')
  if (result.success) {
    // 处理成功
  } else {
    // 处理业务错误（result.message）
    ElMessage.error(result.message)
  }
} catch (error) {
  // 网络错误或异常（已由拦截器自动提示）
  console.error('登录失败:', error)
}
```

## 注意事项

1. **Token 管理**：登录后 Token 自动存储到 localStorage，请求自动携带
2. **CORS 配置**：后端已配置允许 `http://localhost:1314` 跨域
3. **数据来源**：所有数据来自真实后端接口；刷新后数据是否保留取决于后端状态，不存在本地兜底。
