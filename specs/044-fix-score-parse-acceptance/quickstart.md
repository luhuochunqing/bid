# Quickstart: PR !2292 验收缺陷修复验证

## 前置

- 基线分支：`origin/043-harden-score-parse-intake`（PR !2298，含 P0 import 修复）
- 本分支：`agent/zcode/fix-score-parse-acceptance`
- 依赖已安装（node_modules 存在）；后端 Maven 本地仓库可用

## 自动化验证

```bash
# 1. 前端：归一化 + 表格 + 弹窗 + QA 全链路（含 041/043 既有用例零回归）
npx vitest run src/composables/projectDetail/ src/views/Project/stages/components/

# 2. 后端：整体编译（P0 门禁——main 上曾失败，基线 043 已修复）
cd backend && mvn compile

# 3. 后端：scoreparse 测试（50MB 文案改动后仍全绿）
mvn test -Dtest='com.xiyu.bid.scoreparse.**.*Test' -DfailIfNoTests=false
```

预期：三个命令全部通过。

## 手工走查（PRD 对齐）

| 场景 | 操作 | 预期 |
|---|---|---|
| 待确认客观项（FR-001/002/003） | 后端返回某客观项 `estScore: null, status: "PENDING"`，打开抽屉 | 得分列灰字"待确认"（非红色 0）；详情弹窗"预计得分"为"待确认"；合计行不含该项 |
| 真实零分回归 | 某客观项 `estScore: 0, status: "DANGER"` | 得分列红色 0 分（不受影响） |
| 弹窗限高（FR-004） | 打开含长引用/缺失说明/建议的详情 | 弹窗 ≤70vh，内容区内部滚动，标题与关闭按钮可见 |
| 状态视觉（FR-005） | 查看含待确认行的表格 | 待确认=灰色文字+蓝色圆点前缀 |
| 50MB 文案（FR-006） | 上传 >50MB 投标文件（接口层） | 提示"文件大小超过限制（50MB），请压缩后重新上传" |

## 声明一致性（FR-007/008）

- `specs/042-score-parse-v3-acceptance/tasks.md` T03 描述与实现行为一致（手动触发）
- `docs/references/engineering-discipline.md` 含"合并前 CI 必须绿（含后端编译）"条目
