# 测试数据覆盖矩阵

> 本文件与 `test-data-seed.sql` 配套，说明每个测试项目的业务场景、所处阶段和关键状态值，方便逐一对账测试。

---

## 一、数据链路总览

```
标讯 (tenders) ──→ 项目 (projects) ──→ 立项 (project_initiation_details)
                                          │
                                          ├──→ 评标 (project_evaluation)
                                          ├──→ 结果确认 (project_result)
                                          ├──→ 复盘 (project_retrospective)
                                          ├──→ 结项 (project_closure)
                                          └──→ 保证金 (fees)
```

**各页面依赖的数据链路：**

| 页面/组件 | 关键依赖 |
|-----------|---------|
| 标讯列表 | `tenders` 表，按 status/region/industry 过滤 |
| 标讯详情 | `tenders.getDetail(id)` |
| 项目列表 | `projects` 表，按 status/stage 过滤 |
| 项目详情-立项阶段 | `project_initiation_details`，`need_deposit` 控制保证金字段显隐 |
| 项目详情-标书编制 | `projects.documents`，`tasks` |
| 项目详情-评标 | `project_evaluation.sub_stage` 控制 4 种评标状态 chip |
| 项目详情-结果确认 | `project_result.result_type` 控制中标/未中标/流标/弃标 4 套 UI |
| 项目详情-复盘 | `project_retrospective.result_type` + `meeting_*` 控制中标/未中标分析模板 |
| 项目详情-结项 | `project_closure.deposit_return_status` + `need_deposit` 控制 5 种保证金状态 UI |

---

## 二、全状态覆盖矩阵

### 2.1 结果确认（4 种结果类型）

| 项目ID | 项目名称 | Stage | result_type | 页面效果 |
|--------|---------|-------|------------|---------|
| 14 | 省政府框架协议 | CLOSED | WON（中标） | 显示合同信息（中标金额、起止日期） |
| 13 | MES制造执行系统 | RESULT_PENDING | LOST（未中标） | 不显示合同信息，可填写竞争对手 |
| 15 | 轨道交通信号系统 | CLOSED | FAILED（流标） | 显示流标原因摘要输入框 |
| 16 | 新能源汽车零部件 | CLOSED | ABANDONED（弃标） | 显示弃标原因摘要输入框 |

### 2.2 复盘中/未中标分析模板

| 项目ID | result_type | 复盘页面显示 |
|--------|------------|------------|
| 14 | WON（中标） | 中标优势 + 流程亮点 + 后续改进建议 |
| 15 | FAILED（流标）→ 直接结项，跳过复盘 | （不显示复盘表单） |
| 16 | ABANDONED（弃标）→ 直接结项，跳过复盘 | （不显示复盘表单） |
| 21 | LOST（未中标） | 丢标原因复选框 + 流程问题 + 改进措施 |

### 2.3 结项保证金状态（5 种变体 — ★★★ 核心测试矩阵 ★★★）

| 项目ID | 项目名称 | need_deposit | deposit_return_status | 结项页 UI 差异 |
|--------|---------|-------------|----------------------|--------------|
| 20 | 华东政务云 | **NO** | **NA** | 显示"无保证金"，不显示任何保证金字段，可直接操作结项 |
| 12 | 西部云数据中心 | **YES** | **NOT_RETURNED（未退回）** | 显示保证金金额+缴纳方式，退回状态下拉显示"未退回"，**不可结项**（canClose=false） |
| 23 | 华南机场安检 | **YES** | **FULLY_RETURNED（全部退回）** | 显示退回日期选择器 + 上传银行回单凭证 |
| 21 | 西南医院信息化 | **YES** | **TRANSFERRED_TO_FEE（转服务费）** | 显示"转服务费金额"输入框 + 上传证明文件 |
| 22 | 电网调度系统 | **YES** | **PARTIAL_RETURN_PARTIAL_TRANSFER（部分退回+部分转）** | 显示"退回金额" + "转服务费金额" + 上传证明文件 |

### 2.4 结项审核状态

| 项目ID | review_status | 结项页 UI 差异 |
|--------|-------------|--------------|
| 14 | APPROVED | 只读摘要，显示"已结项"，二次招标按钮 |
| 23 | APPROVED | 同上 |
| 15 | REJECTED | 显示"审核驳回"状态 + 驳回原因文本 |
| 12 | DRAFT（草稿） | 可编辑，待提交审核 |

### 2.5 立项页面 — 保证金字段显隐

| 项目ID | need_deposit | 立项页 UI 差异 |
|--------|-------------|--------------|
| 17 | YES（有保证金） | 显示"保证金金额" + "保证金缴纳方式（电汇/保函）" |
| 18 | NO（无保证金） | 隐藏保证金金额和缴纳方式字段 |

### 2.6 评标子状态

| 项目ID | sub_stage | 评标页 chip 状态 |
|--------|----------|----------------|
| 10 | IN_PROGRESS | "评标中" 高亮 |
| 12 | IN_PROGRESS | "评标中" 高亮 |
| 14 | CLOSED | 评标已结束（只读） |

---

## 三、测试要点说明

### 结项页测试（重点）

结项页是差异最大的页面，请按以下顺序逐一验证：

1. **项目20**（无保证金）：进入结项页，确认不显示保证金区块，可直接操作
2. **项目12**（有保证金-未退回）：进入结项页，确认显示保证金金额和缴纳方式，退回状态选"未退回"时无额外字段，**点提交应被拦截**（canClose=false）
3. **项目23**（全部退回）：选择"全部退回"→ 出现退回日期选择器 + 上传凭证
4. **项目21**（转服务费）：选择"转平台服务费"→ 出现金额输入框 + 上传证明文件
5. **项目22**（部分退回+部分转）：选择"部分退回，部分转平台服务费"→ 出现退回金额 + 转服务费金额 + 上传证明文件
6. **项目15**（已驳回）：确认显示驳回原因文字
7. **项目12**（草稿）：确认处于可编辑状态，审核按钮不显示

### 结果确认页测试

1. **项目13**（LOST）：确认选择"未中标"后不显示合同信息字段
2. **项目12**（WON）：确认选择"中标"后显示中标金额、合同起止日期
3. **项目选择"流标"或"弃标"**：确认显示结果摘要输入框

### 复盘页测试

1. **项目14**（WON-已复盘）：确认显示中标分析模板（中标优势+流程亮点）
2. **项目21**（LOST-待复盘）：确认显示丢标分析模板（丢标原因复选框）

---

## 四、数据导入方式

```bash
# 前提：确保 demo-data.sql 已导入
# 1. 本地 worktree 数据库（codex）
docker exec -i xiyu-bid-local-mysql mysql -u xiyu_user -pXiyuDB!2026 \
  --default-character-set=utf8mb4 xiyu_bid_codex \
  < demo-data.sql

docker exec -i xiyu-bid-local-mysql mysql -u xiyu_user -pXiyuDB!2026 \
  --default-character-set=utf8mb4 xiyu_bid_codex \
  < test-data-seed.sql

# 2. 主数据库（main）— 修改 database 名即可
docker exec -i xiyu-bid-local-mysql mysql -u xiyu_user -pXiyuDB!2026 \
  --default-character-set=utf8mb4 xiyu_bid_main \
  < test-data-seed.sql
```

## 五、验证 SQL

导入后执行以下 SQL 验证覆盖率：

```sql
-- 查看全部项目的阶段和结果状态
SELECT p.id, p.name, p.stage, p.status, 
       pr.result_type, 
       pc.review_status, pc.deposit_return_status,
       pid.need_deposit, pid.deposit_amount
FROM projects p
LEFT JOIN project_result pr ON pr.project_id = p.id
LEFT JOIN project_closure pc ON pc.project_id = p.id
LEFT JOIN project_initiation_details pid ON pid.project_id = p.id
ORDER BY p.id;

-- 确认结项保证金状态分布
SELECT pc.deposit_return_status, COUNT(*) as cnt
FROM project_closure pc
GROUP BY pc.deposit_return_status;

-- 确认结果类型分布
SELECT pr.result_type, COUNT(*) as cnt
FROM project_result pr
GROUP BY pr.result_type;
```
