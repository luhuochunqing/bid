---
title: Module 8 AI 能力体系 — 蓝图功能实现对照
space: engineering
category: testing
tags: [testing, 蓝图对照, AI, 智能, 招标拆解, 合规, 商机预测]
sources:
  - .wiki/sources/testing/module-08-ai-test.md
  - .wiki/sources/testing/module-08-ai-test.md
backlinks:
  - _index
created: 2026-05-28
updated: 2026-06-21
health_checked: 2026-06-27
---
> 蓝图章节：§5 AI 能力体系
> 对应飞书蓝图：https://my.feishu.cn/docx/FgLAdRmFho4QhwxncgAcfxKJn0d

## 覆盖度总览

| 蓝图功能 | 实现状态 | 测试方式 | 覆盖情况 |
|---------|---------|---------|---------|
| AI 能力总览 | ✅ 已完成 | API + 手动 | 前端 AICenter.vue 3 分类展示，9 项 AI 能力开关与配置 |
| 招标文件拆解（AI 自动提取主体/预算/时间节点/资质要求） | ✅ 已完成 | API + E2E | ProjectTenderBreakdownController + DocInsightController 完整解析管线 |
| 项目等级评估（规则/AI 自动评估） | ✅ 已完成 | API | ProjectScorePreviewPolicy，POST /api/projects/score-preview |
| 标书初审（AI 合规性检查/文本质量评估） | ✅ 已完成 | API | ComplianceCheckService，TenderAiAnalysisPolicy 逐项检查 |
| 历史方案提取与复用（AI 智能检索/相似度匹配/推荐复用） | 🟡 部分完成 | API + 手动 | BidDraftAgent/TenderDocumentReuse 端点存在，未包装为独立 UI |
| 商机时间预测（历史数据驱动的商机时间节点预测） | 🟡 部分完成 | 手动 | marketprediction 包存在，与 AI Center 集成 UI 未完全建立 |

## 功能 1：AI 能力总览

### 蓝图要求
AI 能力体系涵盖投标准备（4 项）、标书编制（2 项）、团队协作（3 项）共 9 项 AI 能力。

### 实现说明
- 前端：src/views/AI/Center.vue — 3 个 Tab（投标准备/标书编制/团队协作），每个 FeatureCard 含开关、配置入口、使用统计
- API 调用：通过 ai-prompts.js 配置驱动，连接后端 AI 服务

### 测试方式
手动 + API 验证

### 测试示例
```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:18081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"XiyuAdmin2026!"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")

# 验证 AI 功能总开关
curl -s http://127.0.0.1:18081/api/settings \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys, json
data = json.load(sys.stdin)['data']
sysConfig = data.get('systemConfig', {})
print(f'AI 功能总开关 (enableAI): {sysConfig.get(\"enableAI\", \"N/A\")}')
print(f'当前活跃 AI Provider: {data[\"aiModelConfig\"][\"activeProvider\"]}')
for p in data['aiModelConfig']['providers']:
    print(f'  Provider: {p[\"providerName\"]} - enabled={p[\"enabled\"]}')
"

# 通过前端页面验证 9 项能力展示：
# 投标准备：AI 分析、评分点覆盖、竞争情报、ROI 核算
# 标书编制：智能装配、合规雷达
# 团队协作：版本管理、协作中心、自动化任务
```

## 功能 2：招标文件拆解（AI 自动提取主体/预算/时间节点/资质要求）

### 蓝图要求
评分标准提取、资质要求识别、技术要点提取、商务条款解析、废标红线标记、编标任务拆解。

### 实现说明
- 后端：ProjectTenderBreakdownController — 招标文件解析完整端点
  - GET /api/projects/{projectId}/tender-breakdown/readiness — 解析就绪检查
  - POST /api/projects/{projectId}/tender-breakdown — 上传解析
  - GET /api/projects/{projectId}/tender-breakdown/latest — 获取最新结果
  - POST /api/projects/{projectId}/tender-breakdown/reuse-uploaded — 复用已上传文件
- DocInsightController — POST /api/doc-insight/parse 通用文档智能解析
- 核心模型：TenderAiAnalysisPolicy 评估分析结果
  - projectInfo, requirements, scoringCriteria, risks 结构化输出
  - 证据锚定：每个字段携带 sourceExcerpt 和 sectionPath

### 测试方式
API 测试

### 测试示例
```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:18081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"XiyuAdmin2026!"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")

# 1. 创建项目（准备解析环境）
PROJECT_ID=$(curl -s -X POST http://127.0.0.1:18081/api/projects \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "AI解析测试项目",
    "description": "用于测试招标文件拆解功能"
  }' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])" 2>/dev/null)

# 2. 检查解析就绪状态
curl -s "http://127.0.0.1:18081/api/projects/$PROJECT_ID/tender-breakdown/readiness" \
  -H "Authorization: Bearer $TOKEN"

# 3. 获取最新解析结果
curl -s "http://127.0.0.1:18081/api/projects/$PROJECT_ID/tender-breakdown/latest" \
  -H "Authorization: Bearer $TOKEN"

# 4. 测试任务拆解端点（POST 需要文件上传，此处仅验证存在性）
echo "解析需要上传文件: POST /api/projects/{projectId}/tender-breakdown (multipart/form-data)"
echo "任务拆解: POST /api/projects/{projectId}/tasks/decompose"

# 5. 验证项目 AI 卡片
curl -s "http://127.0.0.1:18081/api/projects/$PROJECT_ID/ai-cards" \
  -H "Authorization: Bearer $TOKEN"
```

## 功能 3：项目等级评估（规则/AI 自动评估）

### 蓝图要求
基于提供的评估规则，对项目进行风险等级评估。

### 实现说明
- 后端：ProjectScorePreviewPolicy — 评分预览策略引擎
  - 5 个标准维度（技术能力 30%、财务实力 25%、团队经验 20%、历史业绩 15%、合规性 10%）
  - 风险等级：80-100 低风险、60-79 中等风险、0-59 高风险
- AiDeepCapabilityService.createScorePreview() — 核心入口
- API：POST /api/projects/score-preview

### 测试方式
API 测试

### 测试示例
```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:18081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"XiyuAdmin2026!"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")

# 创建评分预览
curl -s -X POST http://127.0.0.1:18081/api/projects/score-preview \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "projectId": null,
    "tenderId": null,
    "estimatedBudget": 5000000,
    "industryCategory": "IT",
    "projectComplexity": "HIGH",
    "tags": ["技术方案", "集成服务", "运维"],
    "evaluationRules": {
      "minTechScore": 70,
      "minFinanceScore": 60,
      "requiredExperience": "3年以上同类项目经验"
    }
  }' | python3 -m json.tool
```

## 功能 4：标书初审（AI 合规性检查/文本质量评估）

### 蓝图要求
合规性审查：检查是否存在废标条款、格式错误等。

### 实现说明
- 后端：ComplianceCheckService — 6 维度合规检测
  - 格式检查（MEDIUM）、完整性检查（HIGH）、资质检查（HIGH）
  - 签章检查（HIGH）、时效检查（CRITICAL）、报价合规（HIGH）
- TenderAiAnalysisPolicy — AI 分析策略引擎
  - 综合评分 0-100、风险评分、中标率权重
- API：通过 AiDeepCapabilityService.getProjectAiCards() 获取

### 测试方式
API 测试

### 测试示例
```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:18081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"XiyuAdmin2026!"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")

# 通过 AI 分析端点验证合规检查能力
# 创建标讯后，调用 AI 分析
TENDER_ID=$(curl -s -X POST http://127.0.0.1:18081/api/tenders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "合规检查测试标讯",
    "source": "API测试",
    "budget": 1000000,
    "deadline": "2026-06-30T23:59:59",
    "status": "TRACKING"
  }' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])" 2>/dev/null)

echo "标讯 ID: $TENDER_ID"

# AI 分析报告（包含风险检查维度）
curl -s "http://127.0.0.1:18081/api/projects?tenderId=$TENDER_ID" \
  -H "Authorization: Bearer $TOKEN"
```

## 功能 5：历史方案提取与复用（AI 智能检索/相似度匹配/推荐复用）

### 蓝图要求
从知识库中检索历史投标方案，基于相似度匹配推荐可复用的内容。

### 实现说明
- 后端：BidTenderDocumentImportAppService + BidUploadedTenderDocumentReuseAppService
  - POST /api/projects/{projectId}/tender-breakdown/reuse-uploaded — 复用已上传招标文件解析
  - bidmatch 包 — 投标匹配评分模块
- 前端：BidMatchScoringSettingsPanel.vue — 配置评分维度和权重

### 测试方式
API 测试

### 测试示例
```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:18081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"XiyuAdmin2026!"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")

# 尝试复用已上传招标文件（需要项目已有上传文件）
# POST /api/projects/{projectId}/tender-breakdown/reuse-uploaded
echo "复用流程:"
echo "  1. 上传招标文件到项目"
echo "  2. POST /api/projects/{projectId}/tender-breakdown/reuse-uploaded"
echo "  3. 系统解析文件并提取结构化信息"

# 投标匹配评分配置（AI 智能匹配的基础）
curl -s http://127.0.0.1:18081/api/settings \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys, json
data = json.load(sys.stdin)['data']
print('系统配置中投标匹配相关设置:')
print(f'  enableAI: {data[\"systemConfig\"][\"enableAI\"]}')
print(f'  AI Provider: {data[\"aiModelConfig\"][\"activeProvider\"]}')
"
```

## 功能 6：商机时间预测（历史数据驱动的商机时间节点预测）

### 蓝图要求
周期分析、趋势预测、提醒生成；同一招标主体 >= 2 条历史项目数据即可生成预测结果。

### 实现说明
- 后端：marketprediction 包存在但未找到独立 Controller
- 预测规则：基于历史投标数据（招标主体、发布时间、开标时间）
- 数据来源：系统存量历史投标项目数据
- 当前状态：底层模块存在，与前端 AI Center 的集成 UI 部分完成

### 测试方式
手动

### 测试示例
```bash
echo "商机时间预测功能需满足以下条件:"
echo "  1. 系统中有同一招标主体 >= 2 条历史项目数据"
echo "  2. 预测结果在 AI 分析报告中标讯详情页展示"
echo "  3. 自动生成跟进任务提醒"
echo ""
echo "前端验证路径:"
echo "  标讯中心 -> 标讯详情 -> AI 分析报告 -> 商机预测卡片"
echo "  或 AICenter.vue -> 投标准备 Tab -> AI分析配置"
```
