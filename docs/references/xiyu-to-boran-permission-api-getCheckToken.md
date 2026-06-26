# 西域给泊冉权限接口 - 用户 token 校验接口

> 接口名称：用户 token 校验接口
> 接口路径：`GET /oauth/getCheckToken`
> Mock 地址：`https://yapi.ehsy.com/mock/406/oauth/getCheckToken`
> 备注：校验用户 token 是否有效，返回 token 中携带的用户信息及过期时间等元数据

---

## 请求参数

### Headers

| 参数名称      | 参数值                            | 是否必须 | 示例 | 备注 |
| ------------- | --------------------------------- | -------- | ---- | ---- |
| Content-Type  | application/x-www-form-urlencoded | 是       |      |      |
| Authorization |                                   | 是       |      | Bearer token，或直接传 token 值 |

### Query

| 参数名称 | 是否必须 | 示例 | 备注 |
| -------- | -------- | ---- | ---- |
| token    | 是       |      | 待校验的 access_token |

---

## 返回数据

### 返回示例

```json
{
  "code": 0,
  "message": "success",
  "trace": null,
  "data": {
    "aud": [
      "api"
    ],
    "user_name": "06669",
    "scope": [
      "all"
    ],
    "active": true,
    "exp": 1718333834,
    "jti": "QjPjAJIJ_QHAdkWyOZZd4A5bv0k",
    "client_id": "web"
  }
}
```

### 响应字段

| 名称        | 类型       | 是否必须 | 默认值 | 备注                 |
| ----------- | ---------- | -------- | ------ | -------------------- |
| code        | number     | 非必须   |        | 状态码               |
| message     | string     | 非必须   |        | 响应消息             |
| trace       | null       | 非必须   |        | 追踪信息             |
| data        | object     | 非必须   |        | token 校验结果数据   |
| ├ aud       | string[]   | 非必须   |        | 受众列表             |
| ├ user_name | string     | 非必须   |        | 用户名（工号）       |
| ├ scope     | string[]   | 非必须   |        | 授权范围             |
| ├ active    | boolean    | 非必须   |        | token 是否有效       |
| ├ exp       | number     | 非必须   |        | 过期时间戳（秒）     |
| ├ jti       | string     | 非必须   |        | JWT ID               |
| ├ client_id | string     | 非必须   |        | 客户端 ID            |
