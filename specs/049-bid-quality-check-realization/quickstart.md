# Quickstart: 049 验证指南（唯一主线 + 各步测试证据）

**前置 Spec**: [spec.md](./spec.md) | **契约**: [contracts/](./contracts/) | **数据模型**: [data-model.md](./data-model.md)

---

## 0. 实施前置（每次开工）

```bash
git fetch origin && git rebase origin/main          # Sync-First（Constitution）
./scripts/who-touches.sh backend/src/main/java/com/xiyu/bid/compliance   # Lease 检查，退出码 0 方可开工
./scripts/who-touches.sh src/views/Project/stages
```

> 开发环境仅主工作区 `/Users/user/xiyu/worktrees/trae` 可启动（后端 18089 / Sidecar 8009 / MySQL xiyu_bid_main），需 `export XIYU_DEV_CONFIRMED=1`。qoder worktree 只做编辑与 git 操作，测试在主工作区或 CI 执行。

## 1. G2 验证：双链路删除零残留（第零步完成后）

```bash
# 全部应零命中（specs/ 与 docs/ 除外）
grep -rn "bid-document/check\|bid-document/results" --include="*.java" --include="*.js" --include="*.vue" --include="*.http" backend/ src/ api-tests/ e2e/
grep -rn "BidDocumentQualityCheckPolicy\|BidDocumentQualityCheckAppService\|QualityCheckDialog\|runBidDocumentQualityCheck" backend/src src/ e2e/

cd backend && mvn -q compile && mvn -q test        # 后端编译 + 全量单测
cd .. && npm run lint && npm run build             # 前端门禁
```

预期：grep 零命中；后端/前端全绿；`DraftingBidFilesArea.vue` 无"AI标书质量核查"按钮。

## 2. G3 验证：无文件不核查（第一步完成后）

```http
### api-tests/compliance 场景（无投标文件的项目 ID）
POST {{host}}/api/compliance/bid-quality/trigger/{{projectIdWithoutBidDoc}}
```

预期：4xx + "无法执行核查，请先上传投标文件"；DB 中该项目无新任务记录。

单测：三种存储前缀分派（`obs-direct:` / `bid-agent://` / `doc-insight://`）各有用例；`tryResolveLocalPath` 降级路径已删除（无 fallback 测试残留）。

## 3. G1 验证：唯一主线端到端（五步全部完成后，主工作区人工走查）

**前置数据**：

1. 登录主工作区开发环境（`npm run agent:health-check` 确认 sidecar/backend/frontend 健康）
2. 准备一个真实投标 PDF（含封面、资质章节、业绩章节）与一个真实招标 PDF（含评分办法、资格审查、否决条款章节）
3. 创建测试项目，上传两个文件（投标 → BID 类，招标 → TENDER/TENDER_FILE 类）
4. 确认四知识库各有至少一条可匹配的真实记录（资质/品牌授权/人员/业绩）

**走查动作**（对齐 spec §2 操作性定义）：

1. DraftingStage 打开质量核查 Drawer → trigger → 观察进度条活跃（PARSING → CHECKING，LLM 阶段有 currentStep 更新）
2. 报告返回 25 项、3 分组、三态统计正确
3. 随机抽 3 个 FAIL/MANUAL 项逐项验证：
   - 页码 = 真实 PDF 中的实际页（打开原文件对照）
   - quote = 该页真实原文摘录（非写死文案）
   - 招标要求证据来自招标 PDF 解析内容（非 Tender.description）
   - 知识库证据有 recordId，能在对应库中找到该记录
4. 二次确认重新检查（force=true）→ 重跑并覆盖报告
5. 关闭重开 Drawer → 命中 hash 缓存直接出报告（不换文件不重跑）

**异常分支走查**：

- 删除招标 PDF 后重跑 → 比对项 MANUAL + `[REFERENCE_MISSING]`，格式类独立项照常 PASS/FAIL
- 单测模拟 LLM 失败 → 语义项 MANUAL + `[LOW_CONFIDENCE]`，任务不 FAILED

## 4. G4 验证：每变必测（每步提交前）

```bash
cd backend && mvn test                             # 全量单测（含 ArchUnit）
npm run check:front-data-boundaries && npm run check:line-budgets   # 前端门禁
npm run build
```

新增文件行数自查（硬上限 300 行）：`wc -l backend/src/main/java/com/xiyu/bid/compliance/domain/policy/*.java`

## 5. G5 验证：无 Mock / 固定文案残留

```bash
grep -rn "已对照企业专家与项目经理库\|已检索品牌制造商授权\|isDemoMode" backend/src src/ | grep -v specs/
grep -rn "pageNumber, 1\|page, 1" backend/src/main/java/com/xiyu/bid/compliance/   # 硬编码页码抽查
```

预期：零命中。

## 6. G6 收尾：存储口径定案（阻塞合入，不阻塞实施）

产品确认 spec §5.2 方案 A/B 后：方案 B → 修订 PRD §4.4 并在 spec 决策段记录；方案 A → 另开任务改造存储层。确认前不合入 main。
