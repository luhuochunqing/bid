# CRM API Contract

## BaseUrl 路由规则

| 服务域 | 配置项 | 用途 | 默认回退 |
|--------|--------|------|----------|
| 鉴权/组织架构 | `authBaseUrl` | Token、菜单、员工 | `baseUrl` |
| 客户查询 | `customerBaseUrl` | 客户搜索、负责人 | `baseUrl` |
| 消息推送 | `messageBaseUrl` | 企微/站内信 | `baseUrl` |

## 接口路径配置

### Auth Service (authBaseUrl)

| 接口 | 配置键 | 默认路径 | YAPI 参考 |
|------|--------|----------|-----------|
| applyToken | `auth.applyTokenPath` | `/auth/applyToken` | project/406/api/23352 |
| logout | `auth.logoutPath` | `/auth/logout` | project/406/api/23370 |
| menuTree | `auth.menuTreePath` | `/menu/tree` | project/406/api/35642 |
| employee | `auth.employeePath` | `/employee/info` | project/406/api/23358 |

### Customer Service (customerBaseUrl)

| 接口 | 配置键 | 默认路径 | YAPI 参考 |
|------|--------|----------|-----------|
| search | `customer.searchPath` | `/customer/search` | project/509/api/25338 |
| contacts | `customer.contactsPath` | `/customer/contacts/batch` | project/509/api/25259 |

### Message Service (messageBaseUrl)

| 接口 | 配置键 | 默认路径 | YAPI 参考 |
|------|--------|----------|-----------|
| send | `message.sendPath` | `/message/send` | project/557/api/35649 |

## 请求/响应格式

### 请求头
```
Authorization: Bearer {token}
Content-Type: application/json
X-Trace-Id: {traceId}
```

### 响应格式
```json
{
  "code": "0",
  "msg": "success",
  "data": {},
  "success": true
}
```

### 业务成功条件
`code == "0" && success == true`
