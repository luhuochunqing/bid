# Go-Live Execution Plan

## Goal
把当前系统从“可演示/可试点”推进到“具备正式上线前门禁”的状态，优先清零 P0 阻塞项，并保留 mock 作为长期演示模式而不是生产主路径。

## Scope For This Execution Wave
本轮不承诺一次性完成全部上线工作，而是完成第一波必须落地的上线门禁：

1. 去掉用户可见的假闭环入口
2. 为后端补生产最小可观测性和健康检查
3. 建立最小 CI 门禁，确保前后端构建和关键测试可重复执行
4. 修正当前配置中会影响生产稳定性的明显问题
5. 输出剩余 P0/P1 backlog，作为下一轮执行清单

## Phase 1: Production Gate Discovery
- 盘点前端仍直接依赖 `@/api/mock` 的用户可见页面
- 识别仍然以静态文案或内联数组冒充真实系统状态的页面
- 盘点当前仓库是否已具备：CI、Actuator、metrics、health endpoint、部署容器基座
- 识别配置层明显阻塞项

## Phase 2: Remove Visible False Closures
### Target files
- `src/views/System/Settings.vue`
- `src/views/Knowledge/CaseDetail.vue`
- `src/views/Project/Detail.vue`

### Changes
- `Settings.vue`
  - 明确标记为“系统管理演示页”，禁止传播错误 API 契约
  - 移除对 `mockData` 的用户/接口文档主数据依赖，改成静态说明或真实系统状态占位
  - 对未接真实后端的管理能力显示“未纳入正式上线范围”而不是伪装成功
- `CaseDetail.vue`
  - 切到 `knowledgeApi.cases.getDetail()`
  - 相关案例在 `mock` 模式下继续走 mock，在 `api` 模式下基于已加载案例做降级展示，不再直接读 `mockData`
- `Project/Detail.vue`
  - 将仍残留的 `mockData` 依赖收口到统一 API/store 或显式演示边界
  - 保留 mock 模式，但 API 模式下禁止默认指向 `/api/upload` 这类未确认真实后端的入口

## Phase 3: Add Production Observability Baseline
### Backend
- 在 `backend/pom.xml` 中加入 Actuator 和 Prometheus registry
- 在 `backend/src/main/resources/application.yml` 中补：
  - `management.endpoints.web.exposure.include`
  - `health/info/prometheus` 暴露
  - readiness/liveness probes
  - 关键 metrics 开关
- 保持现有业务逻辑不变，只增加运维可见性

### Verification
- 编译通过
- 通过最小 SpringBoot 测试验证 actuator context 可启动

## Phase 4: Add Minimal CI Gate
### Repo
- 新增 `.github/workflows/ci.yml`

### Checks
- frontend: `npm ci && npm run build`
- backend: `mvn -DskipTests compile`
- backend critical tests:
  - `ExpenseControllerIntegrationTest`
  - `BarCertificateControllerIntegrationTest`
- 如果 runner 支持 Docker，再运行 `FlywayMysqlContainerTest`

## Phase 5: Fix Configuration Hazards
- 校正 `application.yml` 中配置层级，确保 `datasource/jpa/flyway/cache/jackson` 归属 `spring` 而不是错误挂载到 `ai`
- 保持现有环境变量兼容，不引入新的生产破坏性默认值
- 复跑关键测试和构建

## Deliverables
- 代码改动：前端去假闭环、后端 observability、CI、配置修正
- 文档：新增 go-live 执行计划
- 输出剩余上线 backlog（若本轮未消化）

## Verification Plan
- `npm run build`
- `VITE_API_MODE=api npm run build`
- `mvn -DskipTests compile`
- `mvn -Dtest=ExpenseControllerIntegrationTest,BarCertificateControllerIntegrationTest test`
- 若 Docker 可用：`mvn -Dtest=FlywayMysqlContainerTest test`

## Risks
- 工作树当前已有大量未提交改动，本轮必须在现有改动上增量推进，不能回滚用户已有工作
- `Project/Detail.vue` 体量较大，需避免把非本轮目标的旁路功能一起重写
- CI 增加后可能暴露此前被本地环境掩盖的问题，这属于预期收益
