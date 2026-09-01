# Quickstart: 验证指南

## 前置
- 任务分支 `agent/qoder/case-obs-download` 已部署测试环境（172.16.38.78）
- 测试库已重导 300 条历史案例（含 `obs_bid_file_key`）

## 场景 1：历史案例直链下载（SC-001/002）
1. 登录测试环境案例库，任选一条 `【历史标书切片】` 案例
2. 点击"标书原文" → 预期：浏览器直接下载原版 docx，文件名=原标书名，5 秒内开始
3. 打开文档确认内容完整（SC-002：300 条抽检 ≥10 条全部可打开）

## 场景 2：系统案例 0 回归（SC-003）
1. 任选一条系统沉淀案例（有档案附件）→ 点击"标书原文"
2. 预期：下载项目档案 ZIP，解压内容含真实附件（同时完成 zip-verify 待办）

## 场景 3：无原文提示（SC-004）
1. 构造/选取无 `obs_bid_file_key` 且档案无附件的案例 → 点击"标书原文"
2. 预期：提示"标书原文未归档"或"暂不可用"，无文件落盘

## 场景 4：链接时效（SC-005）
1. curl 调 `GET /api/cases/{id}/download-url` 取 URL，等 310 秒后再请求该 URL
2. 预期：403（已过期）；重新调端点获取新 URL 可下载

## 命令层验证
```bash
# 签发端点（测试环境，替换 CASE_ID）
curl -s -H "Authorization: Bearer $TOKEN" http://172.16.38.78:8080/api/cases/$CASE_ID/download-url
# 数据层
mysql -h winbid-01.test.rds.ehsy.com -u ea_bid -p winbid -e \
  "SELECT COUNT(*) FROM knowledge_case WHERE obs_bid_file_key IS NOT NULL"  # 预期 300
```
