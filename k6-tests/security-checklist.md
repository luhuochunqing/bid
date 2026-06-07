# 安全测试清单

> 执行前确保后端运行在 `http://127.0.0.1:18080`
> Token 获取: `curl -s -X POST http://127.0.0.1:18080/api/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"XiyuAdmin2026!"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])"`

## 1. JWT 安全

### 1.1 Token 伪造
```bash
# 使用伪造 Token 访问
curl -s -o /dev/null -w 'HTTP %{http_code}\n' \
  http://127.0.0.1:18080/api/tenders?page=0&size=1 \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJmYWtlIn0.fake"
# 预期: 401
```

### 1.2 Token 过期
```bash
# 使用已过期 Token（或等待 token 过期后重放）
# 预期: 401
```

### 1.3 Token 篡改
```bash
# 修改合法 Token 的 payload 后重放
# 拆分 JWT，修改中间段，重新拼接
TOKEN="<合法token>"
PAYLOAD=$(echo "$TOKEN" | cut -d. -f2)
# base64 解码 -> 修改 -> base64 编码
# 预期: 401
```

## 2. 权限绕过

### 2.1 越权访问管理员接口
```bash
# staff 角色访问管理员接口
STAFF_TOKEN=$(curl -s -X POST http://127.0.0.1:18080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"xiaowang","password":"123456"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

# 访问设置（仅 ADMIN）
curl -s -o /dev/null -w 'HTTP %{http_code}\n' \
  http://127.0.0.1:18080/api/settings \
  -H "Authorization: Bearer $STAFF_TOKEN"
# 预期: 403

# 访问审计日志（需 ADMIN/AUDITOR）
curl -s -o /dev/null -w 'HTTP %{http_code}\n' \
  http://127.0.0.1:18080/api/audit \
  -H "Authorization: Bearer $STAFF_TOKEN"
# 预期: 403

# 访问用户管理（需 ADMIN）
curl -s -o /dev/null -w 'HTTP %{http_code}\n' \
  http://127.0.0.1:18080/api/admin/users \
  -H "Authorization: Bearer $STAFF_TOKEN"
# 预期: 403
```

### 2.2 角色枚举
```bash
# 逐一测试各角色权限边界
# 账号: lizong(ADMIN), zhangjingli(MANAGER), xiaowang(STAFF), xiaozhao(AUDITOR)
```

## 3. XSS 防护

### 3.1 输入框 XSS
```bash
# 创建标讯时插入 XSS payload
TOKEN=$(curl -s -X POST http://127.0.0.1:18080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"XiyuAdmin2026!"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

curl -s -X POST http://127.0.0.1:18080/api/tenders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "<script>alert(1)</script>",
    "source": "<img src=x onerror=alert(1)>",
    "budget": 1000,
    "status": "TRACKING"
  }' | python3 -m json.tool
# 预期: 标题被转义存储，返回时不执行脚本
```

### 3.2 URL 参数 XSS
```bash
curl -s -o /dev/null -w 'HTTP %{http_code}\n' \
  "http://127.0.0.1:18080/api/tenders?keyword=<script>alert(1)</script>" \
  -H "Authorization: Bearer $TOKEN"
# 预期: 200（参数被正确编码）
```

## 4. SQL 注入

### 4.1 参数注入
```bash
# 搜索参数注入
curl -s -o /dev/null -w 'HTTP %{http_code}\n' \
  "http://127.0.0.1:18080/api/tenders?page=0&size=10&keyword=' OR 1=1 --" \
  -H "Authorization: Bearer $TOKEN"
# 预期: 200（但不返回额外数据）

# 排序注入
curl -s -o /dev/null -w 'HTTP %{http_code}\n' \
  "http://127.0.0.1:18080/api/tenders?page=0&size=10&sort=createdAt,desc;DROP TABLE tenders--" \
  -H "Authorization: Bearer $TOKEN"
# 预期: 400 或 500（非法排序字段）
```

### 4.2 ID 参数注入
```bash
curl -s -o /dev/null -w 'HTTP %{http_code}\n' \
  "http://127.0.0.1:18080/api/tenders/1 OR 1=1" \
  -H "Authorization: Bearer $TOKEN"
# 预期: 400
```

## 5. CORS 配置

### 5.1 非白名单源
```bash
curl -s -o /dev/null -w 'HTTP %{http_code}\n' \
  -H "Origin: https://evil.com" \
  -H "Authorization: Bearer $TOKEN" \
  http://127.0.0.1:18080/api/tenders?page=0&size=1
# 检查响应头中是否包含 Access-Control-Allow-Origin: https://evil.com
# 预期: 不包含或为白名单源
```

### 5.2 带凭据跨域
```bash
curl -s -D - -o /dev/null \
  -H "Origin: https://evil.com" \
  -H "Authorization: Bearer $TOKEN" \
  http://127.0.0.1:18080/api/tenders?page=0&size=1 2>&1 | grep -i access-control
# 预期: 不包含 Access-Control-Allow-Credentials: true
```

## 6. 敏感信息泄露

### 6.1 错误信息
```bash
# 触发 500 错误，检查是否暴露堆栈信息
curl -s http://127.0.0.1:18080/api/tenders/INVALID_ID \
  -H "Authorization: Bearer $TOKEN" | grep -i "stack\|at java\|at org\|errorCode"
# 预期: 不包含 Java 堆栈信息
```

### 6.2 调试端点
```bash
# 检查常见调试端点
for path in /actuator/env /actuator/beans /h2-console /api/swagger-ui.html /api/docs /api/v2/api-docs; do
  echo -n "$path => "
  curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:18080$path
  echo
done
# 预期: 仅 /actuator/health 和已授权端点可访问
```

## 7. 文件上传安全

```bash
# 上传非白名单类型
curl -s -X POST http://127.0.0.1:18080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/etc/passwd;type=application/x-msdownload"
# 预期: 拒绝或只允许白名单类型

# 路径穿越尝试
curl -s -X POST http://127.0.0.1:18080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@test.txt;filename=../../etc/passwd"
# 预期: 文件名被清洗
```

## 8. 速率限制

```bash
# 快速连续请求触发限流
for i in $(seq 1 100); do
  curl -s -o /dev/null -w "%{http_code} " \
    http://127.0.0.1:18080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"wrong"}'
done
# 预期: 出现 429（Too Many Requests）
```
