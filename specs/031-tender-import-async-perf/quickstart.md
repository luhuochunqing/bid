# Quickstart: 标讯批量导入异步化

## 前置条件

- 主工作区 `/Users/user/xiyu/worktrees/trae`
- `export XIYU_DEV_CONFIRMED=1`
- 数据库栈运行：`docker compose up -d`
- 后端运行：`./scripts/start-backend.sh`（端口 18089）
- 前端运行：`./scripts/start-frontend.sh`（端口 1323）

## 验证步骤

### 1. MDC 修复验证（FR-013~FR-017）

```bash
# 登录获取 token
TOKEN=$(curl -s -X POST http://127.0.0.1:18089/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"xiaowang","password":"123456"}' | jq -r '.data.token')

# 调用任意认证接口
curl -s -X GET http://127.0.0.1:18089/api/tenders \
  -H "Authorization: Bearer $TOKEN" > /dev/null

# 查看后端日志，userId 应为 xiaowang 的 ID，而非 anonymous
# 期望日志：{"userId":"<xiaowang的userId>","roleCode":"bid-Team",...}
# 修复前：{"userId":"anonymous","roleCode":"anonymous",...}
```

### 2. 异步导入验证（FR-001~FR-007）

```bash
# 上传 Excel，应在 3s 内返回 taskId
TASK_RESP=$(curl -s -X POST http://127.0.0.1:18089/api/tenders/import \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Idempotency-Key: $(uuidgen)" \
  -F "file=@/path/to/tender-import-template.xlsx")

echo "$TASK_RESP"
# 期望：{"taskId":"<uuid>","status":"PENDING","message":"导入任务已创建，正在处理"}

TASK_ID=$(echo "$TASK_RESP" | jq -r '.data.taskId')

# 轮询进度
while true; do
  PROGRESS=$(curl -s -X GET "http://127.0.0.1:18089/api/tenders/import/$TASK_ID/progress" \
    -H "Authorization: Bearer $TOKEN")
  STATUS=$(echo "$PROGRESS" | jq -r '.data.status')
  echo "Status: $STATUS, Processed: $(echo "$PROGRESS" | jq -r '.data.processedRows')/$(echo "$PROGRESS" | jq -r '.data.totalRows')"
  if [ "$STATUS" = "COMPLETED" ] || [ "$STATUS" = "PARTIAL_SUCCESS" ] || [ "$STATUS" = "FAILED" ]; then
    break
  fi
  sleep 2
done

# 查看最终结果
echo "$PROGRESS" | jq '.data'
```

### 3. 性能验证（FR-008~FR-012）

```bash
# 准备 500 行 Excel（使用模板填充）
# 上传并计时
time curl -s -X POST http://127.0.0.1:18089/api/tenders/import \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Idempotency-Key: $(uuidgen)" \
  -F "file=@/path/to/500-rows-tender.xlsx" | jq -r '.data.taskId'

# 轮询直到完成，记录端到端耗时
# 期望：<60s
```

### 4. 卡死任务恢复验证

```bash
# 创建任务后立即重启后端
TASK_ID=$(curl -s -X POST ... | jq -r '.data.taskId')
./scripts/dev-services.sh restart backend

# 等待后端启动完成
sleep 30

# 查询任务状态，应为 FAILED
curl -s -X GET "http://127.0.0.1:18089/api/tenders/import/$TASK_ID/progress" \
  -H "Authorization: Bearer $TOKEN" | jq '.data.status'
# 期望："FAILED"
# error_details: [{"rowNumber":0,"field":"system","errorMessage":"服务重启导致任务中断",...}]
```

### 5. Nginx 兜底验证（FR-018，生产环境）

```bash
# 登服务器检查 Nginx 配置
ssh jetty@172.16.38.78 'sudo grep proxy_read_timeout /etc/nginx/conf.d/xiyu-bid.conf'
# 期望：proxy_read_timeout 180s;

# 重载 Nginx
ssh jetty@172.16.38.78 'sudo nginx -t && sudo systemctl reload nginx'
```

## 测试命令

### 后端单元测试

```bash
cd backend
mvn test -Dtest=TenderImportAppServiceTest
mvn test -Dtest=TenderImportProgressServiceTest
mvn test -Dtest=TenderImportAsyncContractTest
mvn test -Dtest=TenderImportControllerTest
```

### 架构测试

```bash
cd backend
mvn test -Dtest=ArchitectureTest
mvn test -Dtest=FPJavaArchitectureTest,MaintainabilityArchitectureTest
```

### 前端测试

```bash
npm run test:unit -- --grep "BulkImportDialog"
npm run test:e2e -- --grep "tender-import-async"
```

### 全量验证

```bash
npm run ci:pre-pr
cd backend && mvn test
```

## 验证清单

- [ ] MDC 修复：已登录用户日志 userId 非 anonymous
- [ ] 异步导入：3s 内返回 taskId
- [ ] 进度查询：轮询可见实时进度
- [ ] 部分成功：失败行明细正确返回
- [ ] 性能：500 行 <60s
- [ ] 卡死恢复：服务重启后任务标记 FAILED
- [ ] Idempotency：重复请求返回首次结果
- [ ] Nginx：proxy_read_timeout 180s
- [ ] 架构测试全绿
- [ ] E2E 全绿
