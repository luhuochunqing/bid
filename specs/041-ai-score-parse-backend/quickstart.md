# Quickstart: AI 评分标准解析 — 后端验证指南

**前置**：主工作区 trae 启动后端（`XIYU_DEV_CONFIRMED=1 ./start.sh`，端口 18089）；登录获取 token（`admin / XiyuAdmin2026!`）。

## 1. 纯核心单测（确定性，无需环境）

```bash
cd backend
# 计分公式与守卫（SC-002/SC-003 的确定性基础）
mvn test -Dtest='ScorePartialScorePolicyTest,ScoreRangeGuardTest,ScoreStatusPolicyTest'
# 召回合并去重
mvn test -Dtest='ScoreItemMergePolicyTest'
# 五类匹配器（mock repository 数据）
mvn test -Dtest='CertMatchServiceTest,PersonMatchServiceTest,ProjectMatchServiceTest,WarehouseMatchServiceTest,BrandMatchServiceTest'
# 架构边界
mvn test -Dtest=ArchitectureTest
```
预期：全绿；`PartialScorePolicy` 覆盖四舍五入/开区间/主观项 null 泄漏=0。

## 2. match 接口确定性验证（SC-005）

```bash
TOKEN=...; BASE=http://127.0.0.1:18089
# 预置知识库数据后：
curl -s -X POST "$BASE/api/knowledge/cert/match" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"certNameKeywords":["ISO9001"],"requiredLevel":"三级","requiredCount":1}'
# 预期：tier/matchRatio/matched 与预置数据一致；过期证书 expired=true
# 无匹配数据：tier=NONE, ratio=0, 200（不抛错）
```
计时断言：单评分项五类串行合计 ≤ 5s。

## 3. 解析链路端到端（含 LLM，主工作区）

```bash
# 3.1 上传招标文件（触发自动解析事件）
curl -s -X POST "$BASE/api/projects/1/bid-agent/tender-documents" \
  -H "Authorization: Bearer $TOKEN" -F "file=@评分标准样例.pdf"
# 3.2 轮询解析状态直至终态
curl -s "$BASE/api/projects/1/score-parse/parse/status" -H "Authorization: Bearer $TOKEN"
# 3.3 查询评分项（阶段 1 结果）
curl -s "$BASE/api/projects/1/score-parse/items" -H "Authorization: Bearer $TOKEN"
```
预期：items 数量/编号/权重与样例文件人工核对一致；主观项 estScore=null；summary 权重合计与文件声明一致（不一致时 weightWarning=true）。

## 4. 打分链路端到端

```bash
# 4.1 未上传标书即打分 → 400 NO_BID_DOCUMENT
curl -s -X POST "$BASE/api/projects/1/score-parse/scoring" -H "Authorization: Bearer $TOKEN"
# 4.2 上传投标文件
curl -s -X POST "$BASE/api/projects/1/score-parse/bid-documents" \
  -H "Authorization: Bearer $TOKEN" -F "file=@投标文件样例.pdf"
# 4.3 触发打分 + 轮询 + 查询结果
curl -s -X POST "$BASE/api/projects/1/score-parse/scoring" -H "Authorization: Bearer $TOKEN"
curl -s "$BASE/api/projects/1/score-parse/scoring/status" -H "Authorization: Bearer $TOKEN"
curl -s "$BASE/api/projects/1/score-parse/results" -H "Authorization: Bearer $TOKEN"
```
预期：客观项得分 ∈ [0, weight]；主观项 actualScore=null 且有 suggestion；未响应项 quote=null + 得 0 分。

## 5. 权限验证（SC-006）

```bash
# 用无该项目权限的账号（如 bid-administration 访问受限项目）调任一接口
# 预期：403，message 指向项目权限
```

## 6. 超时保护（SC-004，缩短时钟验证）

单测注入阈值（如 1s）模拟 PROCESSING 挂起 → `@Scheduled` 扫描将其置 FAILED + timeout_marked=1；断言上次成功 items/results 仍可查询。

## 7. 门禁

```bash
npm run build && cd backend && mvn test
```
