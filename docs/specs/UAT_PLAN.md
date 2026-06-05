# UAT Plan

## Purpose
在正式上线前，用真实业务场景验证系统主流程、角色权限、数据一致性和发布后可用性。

## Roles
- 业务负责人：定义验收口径，签署通过/拒绝
- 销售代表：验证标讯、项目、结果闭环
- 技术代表：验证知识库、协作、文档能力
- 财务/资源代表：验证费用、BAR 证书借用、审批退还
- QA：组织执行、记录问题、回归验证
- 技术负责人：修复 blocker 并给出版本说明

## Environment
- 前端：`VITE_API_MODE=api`
- 后端：Spring Boot + MySQL 8.0 + Redis
- 数据库：已应用 Flyway baseline 与增量迁移
- 监控：`/actuator/health` 可访问，`/actuator/prometheus` 按安全策略可访问或返回 401/403
- 本地全流程演练入口：`bash scripts/release/rehearse-release.sh`
- API 模式 Playwright 基线入口：`bash scripts/test/start-api-e2e-stack.sh` 或直接运行 `npm run test:e2e`

## Entry Criteria
- 前后端构建通过
- 关键后端测试通过
- 数据库备份已完成
- 发布候选版本已冻结
- `docs/COMMERCIAL_SCOPE.md` 已冻结正式版范围
- 已知 P0 缺陷为 0

## UAT Scenarios
### 1. 认证与权限
- 销售账号登录成功
- 退出后会话失效
- 未授权用户不能访问受限接口
- 管理角色可访问系统设置与审计页面

### 2. 标讯到项目主链路
- 查看标讯列表与详情
- 从标讯创建项目
- 项目详情可展示真实基础信息
- 结果录入后能回到列表查询

### 3. Knowledge 主链路
- 查看资质、案例、模板列表
- 新增案例/模板成功
- 案例详情页通过统一 API 层获取数据
- API 模式下不存在页面内联 mock 主数据

### 4. 资源主链路
- 费用列表加载成功
- 费用审批、退还申请、确认退还成功
- BAR 证书列表可查看
- 证书借出、归还、借用记录查询成功

### 5. 数据与观测
- Dashboard 可加载真实聚合数据
- Drill-down 在 API 模式下不展示伪造明细
- `health` 返回 UP
- `prometheus` 按安全策略暴露正常（公开访问或受保护返回 401/403）

## Exit Criteria
- 所有 P0 场景通过
- 无 blocker 级问题
- P1 问题有明确 owner 和修复窗口
- 业务负责人签字确认

## Defect Severity
- P0：阻塞上线，必须修复
- P1：影响主流程，原则上修复后再上线
- P2：不阻塞上线，但需进入首个迭代

## UAT Report Template
- 版本号：
- 测试时间：
- 环境：
- 通过场景数：
- 失败场景数：
- P0/P1/P2 缺陷列表：
- 结论：Go / No-Go

## Execution Output
- 自动执行报告目录：`docs/reports/`
- 自动 UAT 执行脚本：`node scripts/release/run-uat.mjs`
- 正式签字模板：`docs/UAT_SIGNOFF_TEMPLATE.md`
- 签字包生成脚本：`node scripts/release/build-signoff-packet.mjs`
- Playwright API 联调栈启动脚本：`scripts/test/start-api-e2e-stack.sh`
- Playwright API 联调栈停止脚本：`scripts/test/stop-api-e2e-stack.sh`
