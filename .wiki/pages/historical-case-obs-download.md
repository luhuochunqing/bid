---
title: 历史案例原文 OBS 直链下载（spec 058：三分流 + 预签名签发 + 中文键编码分工）
space: engineering
category: guide
tags: [案例库, OBS, 预签名, spec058, obs_bid_file_key, 六边形架构, FP-Java]
created: 2026-09-01
updated: 2026-09-01
health_checked: 2026-09-13
sources:
  - specs/058-historical-case-obs-download/spec.md
  - backend/src/main/java/com/xiyu/bid/casework/application/service/CaseDownloadUrlAppService.java
  - backend/src/main/java/com/xiyu/bid/file/domain/core/ObsObjectKeyPolicy.java
  - src/views/Knowledge/views/useBidCaseSourceActions.js
backlinks:
  - _index
---
# 历史案例原文 OBS 直链下载（spec 058）

> **一句话**：案例库"标书原文"按钮按数据来源三分流——历史切片案例走 OBS 预签名直链下载原版 docx，
> 系统生成案例保留档案 ZIP，无原文案例明确提示；签发端点只信库内 `obs_bid_file_key`，杜绝任意对象签发。

## 分流契约（useBidCaseSourceActions.openBidDocument）

| 案例状态 | 行为 |
|---|---|
| `obsBidFileKey` 非空 | `GET /api/cases/{id}/download-url` → `window.open(签名URL)` |
| 仅 `sourceProjectId` | 档案预检 → `exportProjectArchive`（ZIP，现状不动） |
| 两者皆无 | 提示"标书原文暂不可用" |

端点响应 `{url, filename, expireSeconds:300}`；404 案例不存在 / 409 无原文 / 502 签发失败。

## 关键约定

1. **对象键编码分工**：`knowledge_case.obs_bid_file_key` 存**明文中文键**（`2026年（投标文件）/{项目目录}/{原文件名}.docx`），
   percent-encode 只发生在签发环节；因此 `ObsObjectKeyPolicy.filenameOf` 不做 URL 解码，失败返 `Optional.empty()`。
2. **安全面**：签发端点只收案例 id，对象键只由导入管道写入（入库前 `is_valid_obs_bid_key` 校验：根前缀/无 `..`/非绝对路径）。
3. **架构**：`CaseDownloadUrlAppService` 经端口 `CaseObsBidFileReader`（domain/gateway）+ `CaseObsBidFileReaderAdapter` 读库，
   模型 `CaseObsBidFile` 为 domain/model 顶层 record（FP-Java：端口不得内嵌类型、不抛异常、不依赖 `.gateway.`/`java.net.`）。
4. **CNAME 链路实测**：`widbid-obs.ehsy.com` 自定义域名下载客户端对中文键可用（99.6MB docx 完整性校验通过）。

## 验证事实（测试环境 172.16.38.78，2026-09-01）

- 部署 598f7608c，V1205 Flyway 生效（`knowledge_case.obs_bid_file_key VARCHAR(512) NULL`，配 U1205 回滚脚本）
- 重导 300 条，`obs_bid_file_key` 300/300 非空；端点抽测 200/404/409/403 全通
- 前端三分流契约测试 + 全量 3070 绿；后端定向 28 用例绿（含架构测试）
- 随部署生效 PR !2485 的档案 ZIP 前端 Blob 修复（zip-verify 一并验证）

## 元数据缺陷修正（2026-09-01 后续）

导入脚本两处写侧缺陷导致 300 条切片案例元数据错配，已修复并原地回填：

| 缺陷 | 根因 | 修复 |
|---|---|---|
| 项目状态 174 条显示"未知" | `build_row` 仅写 WON，丢弃 LOST(139)/FAILED(35) | `BID_RESULT_MAP` 全终态映射；前台 `CaseDetailDialog` 补第三态"已流标"（warning） |
| 项目类型 300 条全 NULL | 误读 `projects.industry`（历史项目全空）；真值在 `tenders.project_type`（中文原值） | 加载来源项目 `LEFT JOIN tenders`，经 `PROJECT_TYPE_MAP` 归一为枚举码（前台标签/筛选值域） |

存量回填走脚本 `--backfill-meta` 模式（白名单守卫 + `--dry-run` 预演）：回填后分布为
bid_result = WON 126 / LOST 139 / FAILED 35；project_type = COMPREHENSIVE 135 / INDUSTRIAL 121 / OFFICE 44。
详见 lessons-learned #193。
