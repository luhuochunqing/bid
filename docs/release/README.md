# 发布文档 (release/)

存放发布相关文档，包括部署手册、验收报告、回滚手册、部署报告。

## 目录分类

本目录采用**平铺 + 命名前缀**分类，不使用子目录（保持 git 历史完整）：

| 类别 | 文件命名模式 | 说明 |
|------|------------|------|
| 规范文档 | `*.md`（大写首字母） | 部署手册、环境档案、回滚手册、检查清单 |
| 测试环境部署报告 | `deploy-report-YYYY-MM-DD-Nth.md` | 测试服务器 `172.16.38.78` 的历次部署记录 |
| 生产环境部署报告 | `deploy-report-YYYY-MM-DD-Nst-prod.md` | 生产服务器 `172.16.10.149` 的部署记录 |
| 生产复盘 | `postmortem-YYYY-MM-DD-Nst-prod.md` | 生产环境事故复盘 |
| 客户上线说明 | `customer-go-live-notice.md` | 给客户的正式上线说明 |
| 交接文档 | `handoff-prod-*.md` | 生产环境交接文档 |
| 配置说明 | `nginx-*.md` | Nginx 配置说明 |

## 规范文档清单

| 文件 | 功能 |
|------|------|
| `ACCEPTANCE-2026-05-05.md` | 2026-05-05 版本验收报告 |
| `LIVE_SERVER_DEPLOYMENT_RUNBOOK.md` | 部署手册（测试 + 生产通用） |
| `PROD_ENVIRONMENT_PROFILE.md` | 生产环境档案（IP、端口、配置） |
| `PRODUCTION_RELEASE_PIPELINE.md` | 发布流水线说明 |
| `GO_LIVE_CHECKLIST.md` | 上线检查清单 |
| `PERFORMANCE_SECURITY_BACKUP_MONITORING_DELIVERY.md` | 性能/安全/备份/监控交付文档 |
| `ROLLBACK.md` | 应用回滚手册 |
| `ROLLBACK_RUNBOOK.md` | 回滚操作手册 |
| `CHANGELOG.md` | 变更日志 |

## 生产环境文件（首次上线 2026-07-09）

| 文件 | 说明 |
|------|------|
| `deploy-report-2026-07-09-1st-prod.md` | 首次生产部署报告 |
| `postmortem-2026-07-09-1st-prod.md` | 首次生产部署复盘 |
| `customer-go-live-notice.md` | 客户首次上线说明 |
| `handoff-prod-2026-07-09-spring-config-import.md` | SPRING_CONFIG_IMPORT 交接文档 |
| `nginx-tender-import-timeout.md` | Nginx 标书导入超时配置说明 |

## 更新规则

- 每次重大发布后添加部署报告（测试环境 `Nth`，生产环境 `Nst-prod`）
- 回滚手册根据实际情况更新
- 保留历史版本以供审计
- 生产环境部署报告和复盘文档使用 `-prod` 后缀区分
