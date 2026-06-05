# Go-Live Checklist

## 发布前
- [ ] 候选版本已冻结
- [ ] `docs/COMMERCIAL_SCOPE.md` 已确认正式版白名单与 demo-only 黑名单
- [ ] `npm run build` 通过
- [ ] `VITE_API_MODE=api npm run build` 通过
- [ ] `mvn -DskipTests compile` 通过
- [ ] 关键测试通过
  - [ ] `npm run test:unit` — 前端单元测试全绿（161 files, 969+ tests）
  - [ ] `cd backend && mvn test` — 后端全量测试通过
  - [ ] `npx playwright test` — 完整 E2E 套件通过（36+ spec files）
  - [ ] `mvn test -Dtest=ArchitectureTest` — 架构测试通过
- [ ] 非功能测试完成
  - [ ] 性能测试达标（k6: P95 < 3s, 错误率 < 5%）
  - [ ] 安全扫描无高危漏洞（见 `k6-tests/security-checklist.md`）
  - [ ] 跨浏览器测试通过（Chrome + Firefox + Safari）
- [ ] API 冒烟测试通过（`api-tests/` 各模块核心端点正常）
- [ ] MySQL 8.0 baseline Testcontainers 验证通过
- [ ] 数据库备份已执行并校验产物存在
- [ ] 数据库引擎已确认：MySQL 8.0（`DB_ENGINE=mysql`，`SPRING_PROFILES_ACTIVE=prod,mysql`）
- [ ] 监控面板与告警规则已配置
- [ ] UAT 已通过并签字（`docs/testing/manual-cases/` 全部测试用例 PASS）
- [ ] 已知 P0/P1 缺陷为 0
- [ ] `bash scripts/release/rehearse-release.sh` 已执行并产出报告
- [ ] `Staging Gate` 工作流已通过并上传签字包
- [ ] `Main Release Pipeline` 工作流的 `Post-Merge Gate` 已通过
- [ ] 生产部署所需 GitHub `production` 环境变量与 secrets 已配置

## 发布中
- [ ] 执行 `scripts/release/preflight.sh`
- [ ] 记录当前版本号/提交号
- [ ] 停止流量或进入维护窗口
- [ ] 执行数据库迁移（MySQL 路径 `db/migration-mysql`）
- [ ] 部署后端应用
- [ ] 部署前端静态资源
- [ ] 产物版本与 `main` 提交号一致
- [ ] 检查 `/actuator/health`
- [ ] 检查关键接口返回
- [ ] 执行 `node scripts/release/run-prod-smoke.mjs`
- [ ] 检查前端首页与主链路

## 发布后 30 分钟
- [ ] 登录主流程正常
- [ ] 项目/标讯列表可访问
- [ ] Knowledge 主链路可访问
- [ ] 资源审批与 BAR 证书借用可访问
- [ ] 无高优先级错误告警
- [ ] 数据库连接池稳定
- [ ] Prometheus 按既定策略可访问或被正确保护

## 触发回滚条件
- [ ] 数据库迁移失败
- [ ] 应用无法启动
- [ ] 登录主流程失败
- [ ] 核心接口 5xx 持续出现
- [ ] 无法在 15 分钟内恢复核心业务
